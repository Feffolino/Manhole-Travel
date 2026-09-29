// SPDX-License-Identifier: MIT
package it.ratlab.manholes.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * World-wide state, stored in the overworld's data folder as {@code data/manholes.dat}:
 * <ul>
 *   <li>the global node registry (node id -> dimension, pos, name, origin);</li>
 *   <li>the networks (owner id = FTB team id or player uuid -> set of opened node ids);</li>
 *   <li>structure placements waiting for a chunk that wasn't generated yet.</li>
 * </ul>
 */
public final class ManholeData extends SavedData {
    private static final String NAME = "manholes";
    /** 2 = 1.5.0 (home ownership). Older data gets {@link #migrateLegacyHomes} once at server start. */
    private static final int VERSION = 2;
    private boolean homesNeedMigration;

    private final Map<UUID, NodeRecord> nodes = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Map<Long, UUID>> byPos = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> networks = new HashMap<>();
    /** Per-owner names (owner -> node -> plain text). They win over the node's own name for that owner only. */
    private final Map<UUID, Map<UUID, String>> aliases = new HashMap<>();
    private final List<PendingPlacement> pending = new ArrayList<>();
    private final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<List<PendingPlacement>>> pendingByChunk = new HashMap<>();

    public static ManholeData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ManholeData::new, ManholeData::load), NAME);
    }

    // ---------------------------------------------------------------- nodes

    public Collection<NodeRecord> nodes() {
        return Collections.unmodifiableCollection(nodes.values());
    }

    @Nullable
    public NodeRecord node(UUID id) {
        return nodes.get(id);
    }

    @Nullable
    public NodeRecord nodeAt(ResourceKey<Level> dim, BlockPos pos) {
        Map<Long, UUID> m = byPos.get(dim);
        UUID id = m == null ? null : m.get(pos.asLong());
        return id == null ? null : nodes.get(id);
    }

    /** Adds or moves a node. Returns the stored record. */
    public NodeRecord putNode(UUID id, ResourceKey<Level> dim, BlockPos pos) {
        NodeRecord r = nodes.get(id);
        if (r == null) {
            r = new NodeRecord(id, dim, pos);
            nodes.put(id, r);
        } else if (!r.dimension.equals(dim) || !r.pos.equals(pos)) {
            unindex(r);
            r.dimension = dim;
            r.pos = pos.immutable();
        }
        // A different node registered at the same spot is stale: drop it.
        NodeRecord other = nodeAt(dim, pos);
        if (other != null && other != r) {
            removeNode(other.id);
        }
        byPos.computeIfAbsent(dim, k -> new HashMap<>()).put(pos.asLong(), id);
        setDirty();
        return r;
    }

    public void removeNode(UUID id) {
        NodeRecord r = nodes.remove(id);
        if (r != null) {
            unindex(r);
        }
        for (Set<UUID> set : networks.values()) {
            set.remove(id);
        }
        for (Map<UUID, String> m : aliases.values()) {
            m.remove(id);
        }
        setDirty();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
    }

    private void unindex(NodeRecord r) {
        Map<Long, UUID> m = byPos.get(r.dimension);
        if (m != null && r.id.equals(m.get(r.pos.asLong()))) {
            m.remove(r.pos.asLong());
        }
    }

    /** True if a generated node lies within {@code distance} blocks (horizontal) of pos. */
    public boolean generatedNodeWithin(ResourceKey<Level> dim, BlockPos pos, int distance) {
        if (distance <= 0) {
            return false;
        }
        long d2 = (long) distance * distance;
        for (NodeRecord r : nodes.values()) {
            if (r.generated && r.dimension.equals(dim)) {
                long dx = r.pos.getX() - pos.getX();
                long dz = r.pos.getZ() - pos.getZ();
                if (dx * dx + dz * dz < d2) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- networks

    public boolean isOpen(UUID owner, UUID node) {
        Set<UUID> s = networks.get(owner);
        return s != null && s.contains(node);
    }

    public boolean open(UUID owner, UUID node) {
        boolean added = networks.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(node);
        if (added) {
            setDirty();
            it.ratlab.manholes.travel.NetworkSync.markDirty();
        }
        return added;
    }

    public boolean close(UUID owner, UUID node) {
        Set<UUID> s = networks.get(owner);
        boolean removed = s != null && s.remove(node);
        if (removed) {
            setDirty();
            it.ratlab.manholes.travel.NetworkSync.markDirty();
        }
        return removed;
    }

    /** Opened node ids of an owner, in opening order (only nodes still in the registry). */
    public List<NodeRecord> network(UUID owner) {
        Set<UUID> s = networks.get(owner);
        if (s == null) {
            return List.of();
        }
        List<NodeRecord> out = new ArrayList<>();
        for (UUID id : s) {
            NodeRecord r = nodes.get(id);
            if (r != null) {
                out.add(r);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- per-owner aliases

    /** The owner's alias of a node ("" = none). */
    public String alias(UUID owner, UUID node) {
        Map<UUID, String> m = aliases.get(owner);
        String a = m == null ? null : m.get(node);
        return a == null ? "" : a;
    }

    /** Sets (or with an empty / blank name clears) the owner's alias of a node. Returns true if it changed. */
    public boolean setAlias(UUID owner, UUID node, String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        Map<UUID, String> m = aliases.computeIfAbsent(owner, k -> new HashMap<>());
        String old = name.isEmpty() ? m.remove(node) : m.put(node, name);
        if (m.isEmpty()) {
            aliases.remove(owner);
        }
        boolean changed = !name.equals(old == null ? "" : old);
        if (changed) {
            setDirty();
            it.ratlab.manholes.travel.NetworkSync.markDirty();
        }
        return changed;
    }

    /** What {@code owner} calls the node: its alias if set, else the node's own name (or fallback). */
    public net.minecraft.network.chat.Component displayName(UUID owner, NodeRecord r, HolderLookup.Provider registries) {
        String a = alias(owner, r.id);
        return a.isEmpty() ? r.displayName(registries) : net.minecraft.network.chat.Component.literal(a); // plain text only
    }

    // ---------------------------------------------------------------- pending placements

    public void addPending(PendingPlacement p) {
        pending.add(p);
        indexPending(p);
        setDirty();
    }

    public List<PendingPlacement> pendingFor(ResourceKey<Level> dim, ChunkPos chunk) {
        Long2ObjectOpenHashMap<List<PendingPlacement>> m = pendingByChunk.get(dim);
        if (m == null) {
            return List.of();
        }
        List<PendingPlacement> l = m.get(chunk.toLong());
        return l == null ? List.of() : new ArrayList<>(l);
    }

    public boolean hasPending(ResourceKey<Level> dim, ChunkPos chunk) {
        Long2ObjectOpenHashMap<List<PendingPlacement>> m = pendingByChunk.get(dim);
        return m != null && m.containsKey(chunk.toLong());
    }

    public void removePending(PendingPlacement p) {
        pending.remove(p);
        rebuildPendingIndex();
        setDirty();
    }

    /** Called after columns were removed from a pending placement. */
    public void pendingChanged(PendingPlacement p) {
        if (p.columns.isEmpty()) {
            removePending(p);
        } else {
            rebuildPendingIndex();
            setDirty();
        }
    }

    private void indexPending(PendingPlacement p) {
        Long2ObjectOpenHashMap<List<PendingPlacement>> m = pendingByChunk.computeIfAbsent(p.dimension, k -> new Long2ObjectOpenHashMap<>());
        for (long c : p.columns) {
            long key = ChunkPos.asLong(PendingPlacement.colX(c) >> 4, PendingPlacement.colZ(c) >> 4);
            List<PendingPlacement> l = m.computeIfAbsent(key, k -> new ArrayList<>());
            if (!l.contains(p)) {
                l.add(p);
            }
        }
    }

    private void rebuildPendingIndex() {
        pendingByChunk.clear();
        for (PendingPlacement p : pending) {
            indexPending(p);
        }
    }

    // ---------------------------------------------------------------- save / load

    // ---------------------------------------------------------------- 1.5.0 home ownership migration

    /** True until {@link #migrateLegacyHomes} ran for data saved before 1.5.0. */
    public boolean homesNeedMigration() {
        return homesNeedMigration;
    }

    /**
     * Pre-1.5.0 home manholes have no owner. The owner becomes the first network owner holding the node that resolves
     * to one player (a player id without FTB Teams, a one-member FTB team with it; placing a home opened it for the
     * placer). Anything else stays unowned (counts as shared; the first player who uses it claims it). Returns the
     * number of homes that got an owner.
     */
    public int migrateLegacyHomes(MinecraftServer server) {
        homesNeedMigration = false;
        int n = 0;
        for (NodeRecord r : nodes.values()) {
            if (!r.home || r.owner != null) {
                continue;
            }
            for (Map.Entry<UUID, LinkedHashSet<UUID>> e : networks.entrySet()) {
                if (!e.getValue().contains(r.id)) {
                    continue;
                }
                UUID player = playerOf(server, e.getKey());
                if (player != null) {
                    r.owner = player;
                    r.ownerName = playerName(server, player);
                    r.shared = false;
                    n++;
                    break;
                }
            }
            if (r.owner == null) {
                r.shared = true;
            }
        }
        setDirty();
        if (n > 0) {
            it.ratlab.manholes.Manholes.LOGGER.info("Assigned owners to {} home manholes from a pre-1.5.0 save", n);
        }
        it.ratlab.manholes.travel.NetworkSync.markDirty();
        return n;
    }

    /** A network owner id as one player: the player itself, or the only member of an FTB team. */
    @Nullable
    private static UUID playerOf(MinecraftServer server, UUID owner) {
        Collection<UUID> members = it.ratlab.manholes.compat.Hooks.teams().members(server, owner);
        if (members.size() == 1) {
            return members.iterator().next();
        }
        if (!members.isEmpty()) {
            return null; // a party: we can't tell which member placed it
        }
        return server.getPlayerList().getPlayer(owner) != null || !playerName(server, owner).isEmpty() ? owner : null;
    }

    /** Name of a player (online, or from the profile cache), "" if unknown. */
    public static String playerName(MinecraftServer server, UUID player) {
        net.minecraft.server.level.ServerPlayer p = server.getPlayerList().getPlayer(player);
        if (p != null) {
            return p.getGameProfile().getName();
        }
        net.minecraft.server.players.GameProfileCache cache = server.getProfileCache();
        return cache == null ? "" : cache.get(player).map(com.mojang.authlib.GameProfile::getName).orElse("");
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("version", VERSION);
        ListTag nodeList = new ListTag();
        for (NodeRecord r : nodes.values()) {
            nodeList.add(r.save());
        }
        tag.put("nodes", nodeList);
        ListTag nets = new ListTag();
        for (Map.Entry<UUID, LinkedHashSet<UUID>> e : networks.entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            CompoundTag n = new CompoundTag();
            n.putUUID("owner", e.getKey());
            ListTag ids = new ListTag();
            for (UUID id : e.getValue()) {
                ids.add(NbtUtils.createUUID(id));
            }
            n.put("nodes", ids);
            nets.add(n);
        }
        tag.put("networks", nets);
        ListTag al = new ListTag();
        for (Map.Entry<UUID, Map<UUID, String>> e : aliases.entrySet()) {
            for (Map.Entry<UUID, String> a : e.getValue().entrySet()) {
                CompoundTag t = new CompoundTag();
                t.putUUID("owner", e.getKey());
                t.putUUID("node", a.getKey());
                t.putString("name", a.getValue());
                al.add(t);
            }
        }
        tag.put("aliases", al);
        ListTag pend = new ListTag();
        for (PendingPlacement p : pending) {
            pend.add(p.save());
        }
        tag.put("pending", pend);
        return tag;
    }

    private static ManholeData load(CompoundTag tag, HolderLookup.Provider registries) {
        ManholeData d = new ManholeData();
        d.homesNeedMigration = tag.getInt("version") < VERSION;
        for (Tag t : tag.getList("nodes", Tag.TAG_COMPOUND)) {
            NodeRecord r = NodeRecord.load((CompoundTag) t);
            if (r != null) {
                d.nodes.put(r.id, r);
                d.byPos.computeIfAbsent(r.dimension, k -> new HashMap<>()).put(r.pos.asLong(), r.id);
            }
        }
        for (Tag t : tag.getList("networks", Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag) t;
            LinkedHashSet<UUID> set = new LinkedHashSet<>();
            for (Tag idTag : n.getList("nodes", Tag.TAG_INT_ARRAY)) {
                set.add(NbtUtils.loadUUID(idTag));
            }
            d.networks.put(n.getUUID("owner"), set);
        }
        for (Tag t : tag.getList("aliases", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            if (a.hasUUID("owner") && a.hasUUID("node") && !a.getString("name").isEmpty()) {
                d.aliases.computeIfAbsent(a.getUUID("owner"), k -> new HashMap<>()).put(a.getUUID("node"), a.getString("name"));
            }
        }
        for (Tag t : tag.getList("pending", Tag.TAG_COMPOUND)) {
            PendingPlacement p = PendingPlacement.load((CompoundTag) t);
            if (p != null && !p.columns.isEmpty()) {
                d.pending.add(p);
            }
        }
        d.rebuildPendingIndex();
        return d;
    }

    /** A structure placement whose candidate columns are in chunks that weren't generated yet. */
    public static final class PendingPlacement {
        public final ResourceKey<Level> dimension;
        public final String ruleId;
        public final String structureId;
        public final int minY;
        public final int maxY;
        /** Remaining candidate columns, packed with {@link #col(int, int)}, in preference order. */
        public final List<Long> columns;

        public PendingPlacement(ResourceKey<Level> dimension, String ruleId, String structureId, int minY, int maxY, List<Long> columns) {
            this.dimension = dimension;
            this.ruleId = ruleId;
            this.structureId = structureId;
            this.minY = minY;
            this.maxY = maxY;
            this.columns = new ArrayList<>(columns);
        }

        public static long col(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }

        public static int colX(long c) {
            return (int) (c >> 32);
        }

        public static int colZ(long c) {
            return (int) c;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("dim", dimension.location().toString());
            t.putString("rule", ruleId);
            t.putString("structure", structureId);
            t.putInt("minY", minY);
            t.putInt("maxY", maxY);
            long[] arr = new long[columns.size()];
            for (int i = 0; i < arr.length; i++) {
                arr[i] = columns.get(i);
            }
            t.put("columns", new LongArrayTag(arr));
            return t;
        }

        @Nullable
        static PendingPlacement load(CompoundTag t) {
            ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
            if (dim == null) {
                return null;
            }
            List<Long> cols = new ArrayList<>();
            for (long c : t.getLongArray("columns")) {
                cols.add(c);
            }
            return new PendingPlacement(ResourceKey.create(Registries.DIMENSION, dim), t.getString("rule"), t.getString("structure"),
                    t.getInt("minY"), t.getInt("maxY"), cols);
        }
    }
}
