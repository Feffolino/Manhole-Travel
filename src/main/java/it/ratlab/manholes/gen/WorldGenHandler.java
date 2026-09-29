// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.ManholeData.PendingPlacement;
import it.ratlab.manholes.data.Names;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * World generation of manholes.
 * <p>
 * Hook: {@link ChunkEvent.Load} with {@code isNewChunk()} (fired once, on the main thread, when a freshly generated chunk
 * reaches FULL status). There we read the structure starts <b>whose start chunk is this chunk</b> (so each start is
 * handled once) and roll the rules with a random seeded from world seed + start chunk + rule id. The actual placement is
 * done at the end of the level tick (never inside the chunk-load callback). Candidate spots in chunks that are not
 * generated yet are kept in {@link ManholeData} and tried again when those chunks load.
 */
public final class WorldGenHandler {
    private static final int JOBS_PER_TICK = 64;
    /** Extra Y range around a structure's box for ring spots. */
    static final int RING_Y_SLACK = 24;
    private static final Map<ResourceKey<Level>, ArrayDeque<Job>> JOBS = new HashMap<>();
    private static final Map<ResourceKey<Level>, ArrayDeque<ChunkPos>> PENDING_CHUNKS = new HashMap<>();

    /** One rolled placement: try the candidate columns in order, place at the first valid spot. */
    public record Job(String ruleId, @Nullable String structureId, int minY, int maxY, List<Long> columns, Direction facing) {}

    private WorldGenHandler() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(WorldGenHandler::onChunkLoad);
        bus.addListener(WorldGenHandler::onLevelTick);
        bus.addListener(WorldGenHandler::onServerStopping);
    }

    /** Runs what's still queued (levels are still there), then forgets it so nothing leaks into the next world. */
    private static void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (Map.Entry<ResourceKey<Level>, ArrayDeque<Job>> e : JOBS.entrySet()) {
            ServerLevel level = event.getServer().getLevel(e.getKey());
            while (level != null && !e.getValue().isEmpty()) {
                runJob(level, e.getValue().poll());
            }
        }
        JOBS.clear();
        PENDING_CHUNKS.clear();
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        if (ManholesConfig.b(ManholesConfig.DISABLE_ALL_GENERATION)) {
            return;
        }
        if (event.isNewChunk()) {
            List<Job> jobs = rollChunk(level, chunk);
            if (!jobs.isEmpty()) {
                JOBS.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).addAll(jobs);
            }
        }
        if (ManholeData.get(level.getServer()).hasPending(level.dimension(), chunk.getPos())) {
            PENDING_CHUNKS.computeIfAbsent(level.dimension(), k -> new ArrayDeque<>()).add(chunk.getPos());
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        ArrayDeque<Job> jobs = JOBS.get(level.dimension());
        ArrayDeque<ChunkPos> pend = PENDING_CHUNKS.get(level.dimension());
        if ((jobs == null || jobs.isEmpty()) && (pend == null || pend.isEmpty())) {
            return;
        }
        int budget = JOBS_PER_TICK;
        while (jobs != null && !jobs.isEmpty() && budget-- > 0) {
            runJob(level, jobs.poll());
        }
        while (pend != null && !pend.isEmpty() && budget-- > 0) {
            runPending(level, pend.poll());
        }
    }

    // ---------------------------------------------------------------- rolling

    /** Rolls all rules for a chunk (structure starts beginning here + scatter rules). Deterministic. */
    public static List<Job> rollChunk(ServerLevel level, ChunkAccess chunk) {
        List<Job> out = new ArrayList<>();
        long seed = level.getSeed();
        ChunkPos cp = chunk.getPos();
        List<SpawnRule> structureRules = SpawnRuleManager.structureRules();
        if (!structureRules.isEmpty()) {
            Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
            for (Map.Entry<Structure, StructureStart> e : chunk.getAllStarts().entrySet()) {
                StructureStart start = e.getValue();
                if (start == null || !start.isValid() || !start.getChunkPos().equals(cp)) {
                    continue;
                }
                Holder<Structure> holder = reg.wrapAsHolder(e.getKey());
                ResourceLocation sid = reg.getKey(e.getKey());
                if (sid == null || Blacklist.structure(holder, reg)) {
                    continue;
                }
                for (SpawnRule rule : structureRules) {
                    if (!dimensionOk(rule, level) || !rule.structures.matches(holder, reg)
                            || rule.exclude.matchesExplicit(holder, reg)) {
                        continue;
                    }
                    RandomSource rng = RandomSource.create(mix(seed, cp.toLong(), rule.id.hashCode(), sid.hashCode()));
                    for (int i = 0; i < rule.maxPerStructure; i++) {
                        if (rng.nextDouble() < rule.chance) {
                            out.add(structureJob(rule, sid, start.getBoundingBox(), rng));
                        }
                    }
                }
            }
        }
        for (SpawnRule rule : SpawnRuleManager.scatterRules()) {
            if (!dimensionOk(rule, level)) {
                continue;
            }
            RandomSource rng = RandomSource.create(mix(seed, cp.toLong(), rule.id.hashCode(), 0x5CA77E5));
            if (rng.nextDouble() < rule.chancePerChunk) {
                int n = ManholesConfig.i(ManholesConfig.PLACEMENT_ATTEMPTS);
                List<Long> cols = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    cols.add(PendingPlacement.col(cp.getMinBlockX() + rng.nextInt(16), cp.getMinBlockZ() + rng.nextInt(16)));
                }
                out.add(new Job(rule.id.toString(), null, level.getMinBuildHeight(), level.getMaxBuildHeight(), cols,
                        Direction.Plane.HORIZONTAL.getRandomDirection(rng)));
            }
        }
        return out;
    }

    /**
     * Candidate columns on a square ring around the structure (1.5.0): each one at a whole-block distance
     * {@code minOffset..maxOffset} (per axis, i.e. Chebyshev) outside the bounding box, spread along the ring's
     * perimeter. Deterministic from {@code rng}.
     */
    public static Job structureJob(SpawnRule rule, ResourceLocation sid, BoundingBox box, RandomSource rng) {
        int n = ManholesConfig.i(ManholesConfig.PLACEMENT_ATTEMPTS);
        List<Long> cols = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int d = rule.minOffset + (rule.maxOffset > rule.minOffset ? rng.nextInt(rule.maxOffset - rule.minOffset + 1) : 0);
            d = Math.max(1, d); // never on the box itself
            int x0 = box.minX() - d;
            int x1 = box.maxX() + d;
            int z0 = box.minZ() - d;
            int z1 = box.maxZ() + d;
            int w = x1 - x0;
            int hgt = z1 - z0;
            int t = rng.nextInt(2 * (w + hgt));
            int x;
            int z;
            if (t < w) {
                x = x0 + t;
                z = z0;
            } else if (t < w + hgt) {
                x = x1;
                z = z0 + (t - w);
            } else if (t < 2 * w + hgt) {
                x = x1 - (t - w - hgt);
                z = z1;
            } else {
                x = x0;
                z = z1 - (t - 2 * w - hgt);
            }
            cols.add(PendingPlacement.col(x, z));
        }
        // The ring can sit higher or lower than the structure (terrain): a wider Y window than the box.
        return new Job(rule.id.toString(), sid.toString(), box.minY() - RING_Y_SLACK, box.maxY() + RING_Y_SLACK, cols,
                Direction.Plane.HORIZONTAL.getRandomDirection(rng));
    }

    /** Horizontal distance (per axis) of a column from the box, 0 inside it. */
    public static int ringDistance(BoundingBox box, int x, int z) {
        int dx = x < box.minX() ? box.minX() - x : x > box.maxX() ? x - box.maxX() : 0;
        int dz = z < box.minZ() ? box.minZ() - z : z > box.maxZ() ? z - box.maxZ() : 0;
        return Math.max(dx, dz);
    }

    /**
     * True if pos is inside (or within {@code margin} blocks of) the bounding box of any structure start referenced by
     * the loaded chunks around it. Never loads or generates a chunk.
     */
    public static boolean insideAnyStructure(ServerLevel level, BlockPos pos, int margin) {
        net.minecraft.world.level.StructureManager sm = level.structureManager();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (int dx : new int[] {0, -margin, margin}) {
            for (int dz : new int[] {0, -margin, margin}) {
                int cx = (pos.getX() + dx) >> 4;
                int cz = (pos.getZ() + dz) >> 4;
                if (!seen.add(ChunkPos.asLong(cx, cz)) || level.getChunkSource().getChunkNow(cx, cz) == null) {
                    continue;
                }
                for (StructureStart s : sm.startsForStructure(new ChunkPos(cx, cz), st -> true)) {
                    if (s.isValid() && s.getBoundingBox().inflatedBy(margin).isInside(pos)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean dimensionOk(SpawnRule rule, ServerLevel level) {
        if (Blacklist.dimension(level)) {
            return false;
        }
        return rule.dimensions.isEmpty() || rule.dimensions.contains(level.dimension().location().toString());
    }

    private static long mix(long seed, long a, int b, int c) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L);
        h ^= (long) b * 0xC2B2AE3D27D4EB4FL;
        h ^= (long) c * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        return h * 0xBF58476D1CE4E5B9L;
    }

    // ---------------------------------------------------------------- running

    /** Runs a job now. Returns true if a manhole was placed. */
    public static boolean runJob(ServerLevel level, Job job) {
        SpawnRule rule = SpawnRuleManager.byId().get(ResourceLocation.tryParse(job.ruleId()));
        if (rule == null) {
            return false;
        }
        List<Long> unloaded = new ArrayList<>();
        for (long c : job.columns()) {
            int x = PendingPlacement.colX(c);
            int z = PendingPlacement.colZ(c);
            LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) {
                unloaded.add(c);
                continue;
            }
            BlockPos pos = findSpot(level, chunk, rule, x, z, job.minY(), job.maxY());
            if (pos != null && tryPlace(level, pos, rule, job.structureId(), job.facing())) {
                return true;
            }
        }
        if (!unloaded.isEmpty()) {
            ManholeData.get(level.getServer()).addPending(new PendingPlacement(level.dimension(), job.ruleId(),
                    job.structureId() == null ? "" : job.structureId(), job.minY(), job.maxY(), unloaded));
            Manholes.LOGGER.debug("Manhole rule {} ({}) waits for {} candidate spots in ungenerated chunks",
                    job.ruleId(), job.structureId(), unloaded.size());
        } else {
            Manholes.LOGGER.debug("No valid manhole spot for rule {} in {}", job.ruleId(), job.structureId());
        }
        return false;
    }

    private static void runPending(ServerLevel level, ChunkPos cp) {
        ManholeData data = ManholeData.get(level.getServer());
        LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (chunk == null) {
            return;
        }
        for (PendingPlacement p : data.pendingFor(level.dimension(), cp)) {
            SpawnRule rule = SpawnRuleManager.byId().get(ResourceLocation.tryParse(p.ruleId));
            if (rule == null || ManholesConfig.b(ManholesConfig.DISABLE_ALL_GENERATION)) {
                data.removePending(p);
                continue;
            }
            boolean placed = false;
            for (long c : new ArrayList<>(p.columns)) {
                int x = PendingPlacement.colX(c);
                int z = PendingPlacement.colZ(c);
                if ((x >> 4) != cp.x || (z >> 4) != cp.z) {
                    continue;
                }
                p.columns.remove(c);
                BlockPos pos = findSpot(level, chunk, rule, x, z, p.minY, p.maxY);
                if (pos != null && tryPlace(level, pos, rule, p.structureId.isEmpty() ? null : p.structureId,
                        Direction.Plane.HORIZONTAL.getRandomDirection(level.random))) {
                    placed = true;
                    break;
                }
            }
            if (placed) {
                data.removePending(p);
            } else {
                if (p.columns.isEmpty()) {
                    Manholes.LOGGER.debug("No valid manhole spot for rule {} in {}", p.ruleId, p.structureId);
                }
                data.pendingChanged(p);
            }
        }
    }

    private static boolean tryPlace(ServerLevel level, BlockPos pos, SpawnRule rule, @Nullable String structureId, Direction facing) {
        if (Blacklist.dimension(level) || Blacklist.structure(level, structureId)) {
            return true; // blacklisted since it was queued: drop the whole placement
        }
        ManholeData data = ManholeData.get(level.getServer());
        int minDist = rule.minDistance >= 0 ? rule.minDistance : ManholesConfig.i(ManholesConfig.MIN_DISTANCE);
        if (data.generatedNodeWithin(level.dimension(), pos, minDist)) {
            Manholes.LOGGER.debug("Manhole rule {} skipped at {}: another manhole within {} blocks", rule.id, pos, minDist);
            return true; // spacing blocks the whole placement, don't try the other candidates
        }
        String name = rule.name;
        if (name == null && rule.nameFromStructure && structureId != null) {
            ResourceLocation sid = ResourceLocation.tryParse(structureId);
            if (sid != null) {
                name = Names.toRaw(Names.fromStructure(sid), level.registryAccess());
            }
        }
        GenerateContext ctx = new GenerateContext(level, pos, rule.id.toString(), structureId, name);
        if (!Hooks.kube().generate(ctx)) {
            Manholes.LOGGER.debug("Manhole at {} (rule {}) cancelled by a script", pos, rule.id);
            return true;
        }
        if (!ctx.pos.equals(pos)) {
            // A script moved the spot (event.setPos): check the new one physically (air, full floor, no fluids,
            // inside the world and loaded) and against the spacing again. The rule's block / biome filters are the
            // script's business and are not re-applied.
            LevelChunk moved = level.getChunkSource().getChunkNow(ctx.pos.getX() >> 4, ctx.pos.getZ() >> 4);
            if (moved == null || !validPhysical(level, moved, ctx.pos)) {
                Manholes.LOGGER.warn("Manhole rule {}: a script moved the spot from {} to {}, which is not valid (or not loaded); skipped",
                        rule.id, pos, ctx.pos);
                return true;
            }
            if (data.generatedNodeWithin(level.dimension(), ctx.pos, minDist)) {
                Manholes.LOGGER.debug("Manhole rule {} skipped at moved spot {}: another manhole within {} blocks", rule.id, ctx.pos, minDist);
                return true;
            }
        }
        ManholesAPI.placeGenerated(level, ctx.pos, facing, ctx.ruleId, structureId, ctx.name, rule.block());
        Manholes.LOGGER.debug("Generated manhole at {} {} (rule {}, structure {})", level.dimension().location(), ctx.pos, rule.id, structureId);
        return true;
    }

    // ---------------------------------------------------------------- spot checks

    @Nullable
    static BlockPos findSpot(ServerLevel level, LevelChunk chunk, SpawnRule rule, int x, int z, int minY, int maxY) {
        boolean surface = rule.type == SpawnRule.Type.SCATTER || rule.surfaceOnly;
        if (surface) {
            int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            BlockPos pos = new BlockPos(x, top + 1, z);
            if (rule.type == SpawnRule.Type.STRUCTURE && (pos.getY() < minY - 1 || pos.getY() > maxY + 3)) {
                return null;
            }
            return valid(level, chunk, rule, pos, true) ? pos : null;
        }
        int hi = Math.min(maxY + 1, level.getMaxBuildHeight() - 2);
        int lo = Math.max(minY, level.getMinBuildHeight() + 1);
        for (int y = hi; y >= lo; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (valid(level, chunk, rule, pos, false)) {
                return pos;
            }
        }
        return null;
    }

    static boolean valid(ServerLevel level, LevelChunk chunk, SpawnRule rule, BlockPos pos, boolean surface) {
        if (!validPhysical(level, chunk, pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = chunk.getBlockState(below);
        Registry<net.minecraft.world.level.block.Block> blocks = BuiltInRegistries.BLOCK;
        Holder<net.minecraft.world.level.block.Block> groundHolder = ground.getBlockHolder();
        if (!rule.onBlocks.isEmpty() && !rule.onBlocks.matches(groundHolder, blocks)) {
            return false;
        }
        if (rule.avoidBlocks.matchesExplicit(groundHolder, blocks)) {
            return false;
        }
        if (surface && pos.getY() <= chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15)) {
            return false; // under a roof / leaves
        }
        if (Blacklist.biome(level, pos)) {
            return false;
        }
        if (rule.type == SpawnRule.Type.STRUCTURE && insideAnyStructure(level, pos, rule.margin)) {
            return false; // around structures, never inside one (any structure)
        }
        if (rule.type == SpawnRule.Type.SCATTER && !rule.biomes.isEmpty()) {
            Holder<Biome> biome = level.getBiome(pos);
            if (!rule.biomes.matches(biome, level.registryAccess().registryOrThrow(Registries.BIOME))) {
                return false;
            }
        }
        return true;
    }

    /** Rule-independent checks: inside the world, 2 air blocks, a full solid floor, no fluid on or next to the spot. */
    static boolean validPhysical(ServerLevel level, LevelChunk chunk, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight() - 2) {
            return false;
        }
        BlockState at = chunk.getBlockState(pos);
        BlockState above = chunk.getBlockState(pos.above());
        if (!at.isAir() || !above.isAir()) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState ground = chunk.getBlockState(below);
        if (!ground.isCollisionShapeFullBlock(level, below) || ground.getBlock() instanceof it.ratlab.manholes.block.ManholeBlock || ground.hasBlockEntity()) {
            return false;
        }
        if (!noFluid(level, pos) || !noFluid(level, below)) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!noFluid(level, pos.relative(d)) || !noFluid(level, below.relative(d))) {
                return false;
            }
        }
        return true;
    }

    /** Fluid check that never loads a chunk (unloaded neighbours count as dry). */
    private static boolean noFluid(ServerLevel level, BlockPos p) {
        LevelChunk c = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
        return c == null || c.getFluidState(p).isEmpty();
    }

    // ---------------------------------------------------------------- commands

    /** /manholes regen here: rolls this chunk's rules again and runs them now. Returns jobs rolled. */
    public static int regenChunk(ServerLevel level, ChunkPos cp) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (chunk == null) {
            return 0;
        }
        List<Job> jobs = rollChunk(level, chunk);
        for (Job j : jobs) {
            runJob(level, j);
        }
        return jobs.size();
    }
}
