// SPDX-License-Identifier: MIT
package it.ratlab.manholes.block;

import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.Names;
import it.ratlab.manholes.data.NodeRecord;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the stable node id (generated on first load) and the optional name of a manhole.
 * NBT keys: {@code node_id} (UUID), {@code name} (plain text or JSON text component),
 * {@code rule}, {@code structure}, {@code generated}, {@code rust} (0-3; derived from the node id on first registration,
 * always 0 for home manholes). Setting {@code name} in a structure NBT or with
 * {@code /data merge block ... {name:"..."}} names the node. The look comes from the block ({@link ManholeBlock#look()}).
 * Legacy (1.3.0) {@code look} NBT on a {@code manholes:manhole} block ({@code hatch}, {@code grate}, {@code cave},
 * {@code city}) is converted on load: the block is swapped for the matching cover block, keeping the node.
 */
public class ManholeBlockEntity extends BlockEntity {
    @Nullable
    private UUID nodeId;
    private String name = "";
    private String ruleId = "";
    private String structureId = "";
    private boolean generated;
    /** -1 = not set yet (derived from the node id on registration). */
    private int rust = -1;
    /** Home manholes (1.5.0): owner uuid (null = unowned), owner name, share-with-team flag. A copy of the node's. */
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private boolean shared;
    /** 1.3.0 {@code look} NBT read on load, converted to a block swap by {@link #convertLegacyLook()}; never saved. */
    private String legacyLook = "";

    // Client-side animation state (never saved).
    /** Game time the last open/close animation started, or Long.MIN_VALUE (show the end state). */
    public long animStart = Long.MIN_VALUE;
    /**
     * 1.7.2: true while the block entity renderer draws the moving cover; the chunk mesh leaves the cover out meanwhile
     * (model data {@link #ANIMATING}). At rest the cover is part of the chunk mesh and the renderer draws nothing.
     */
    public boolean animating;
    /**
     * Set by the client: called on the client when a cover's open state changes (plays the look's sound). Returns true
     * if the change is animated (a usable look and {@code animateCovers}).
     */
    @Nullable
    public static java.util.function.Predicate<ManholeBlockEntity> clientAnimStarted;

    /** 1.7.2 model data (client): the effective rust level 0..3, for the condition overlays in the chunk mesh. */
    public static final ModelProperty<Integer> RUST = new ModelProperty<>();
    /** 1.7.2 model data (client): true while the renderer animates the cover (the chunk mesh is empty then). */
    public static final ModelProperty<Boolean> ANIMATING = new ModelProperty<>();

    public ManholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MANHOLE_BE.get(), pos, state);
    }

    @Nullable
    public UUID nodeId() {
        return nodeId;
    }

    public String name() {
        return name;
    }

    /** Deterministic rust level 0..3 of a node id (murmur3 fmix64 of the uuid bits, mod 4). */
    public static int rustFor(UUID id) {
        long h = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 29);
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (int) Math.floorMod(h, 4L);
    }

    /** Rust level 0..3 (home manholes: always 0). */
    public int rust() {
        if (isHome()) {
            return 0;
        }
        if (rust >= 0) {
            return rust;
        }
        return nodeId == null ? 0 : rustFor(nodeId);
    }

    public void setRust(int level) {
        this.rust = Math.max(0, Math.min(3, level));
        setChanged();
        syncToClients();
    }

    /**
     * 1.6.0: clients need the condition level to draw the cover's condition overlay. Schedules a block entity update
     * packet (next server task, so it's safe during chunk loading and NBT edits). Server only; no-op elsewhere.
     */
    public void syncToClients() {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        net.minecraft.server.MinecraftServer server = sl.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), () -> {
            if (!isRemoved() && sl.isLoaded(worldPosition) && sl.getBlockEntity(worldPosition) == this) {
                sl.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            }
        }));
    }

    /** Update tag (chunk data and block entity packets): only {@code rust}, the effective level (homes 0). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rust", rust());
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    /** Client: reads only {@code rust} from the update tag (the rest of the node lives on the server). */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("rust")) {
            int before = rust();
            rust = Math.max(0, Math.min(3, tag.getInt("rust")));
            if (rust() != before) {
                refreshMesh(false); // 1.7.2: the condition overlays are in the chunk mesh
            }
        }
    }

    /** 1.7.2 (client): rust level and animation state for {@code CoverBakedModel}. Always called on the client thread. */
    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(RUST, rust()).with(ANIMATING, animating).build();
    }

    /**
     * 1.7.2 (client): the model data changed; refresh it and rebuild this cover's chunk section ({@code immediate} =
     * on the main thread in the next frame, used when switching between the animated renderer and the mesh so there's
     * no frame without a cover). No-op on the server.
     */
    public void refreshMesh(boolean immediate) {
        if (level == null || !level.isClientSide) {
            return;
        }
        requestModelDataUpdate();
        BlockState s = getBlockState();
        level.sendBlockUpdated(worldPosition, s, s, immediate ? net.minecraft.world.level.block.Block.UPDATE_IMMEDIATE : 0);
    }

    /** 1.7.2 (client): the renderer saw the animation reach its end; hand the cover back to the chunk mesh. */
    public void finishAnimation() {
        if (animating) {
            animating = false;
            animStart = Long.MIN_VALUE;
            refreshMesh(true);
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net,
            net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider registries) {
        handleUpdateTag(pkt.getTag(), registries);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public boolean shared() {
        return shared;
    }

    /** Sets the owner of a home manhole (placement / claim). {@code shared} as given. Updates the node. */
    public void setOwner(@Nullable UUID owner, String ownerName, boolean shared) {
        this.owner = owner;
        this.ownerName = ownerName == null ? "" : ownerName;
        this.shared = shared;
        setChanged();
        NodeRecord r = nodeId != null && level instanceof ServerLevel sl ? ManholeData.get(sl.getServer()).node(nodeId) : null;
        if (r != null) {
            r.owner = this.owner;
            r.ownerName = this.ownerName;
            r.shared = this.shared;
        }
        syncRecord();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
    }

    /** The share-with-team flag of a home manhole. */
    public void setShared(boolean shared) {
        setOwner(owner, ownerName, shared);
    }

    /** The look id, from the block ({@code manhole}, {@code hatch}, {@code cave}, ...). */
    public String look() {
        return getBlockState().getBlock() instanceof ManholeBlock b ? b.look() : "city";
    }

    /**
     * Look ids of 1.3.0 that became their own block. Since 1.5.0 {@code manholes:manhole} is an alias of
     * {@code manholes:city_manhole}, so the old block arrives here as a city manhole still carrying the look NBT.
     */
    @Nullable
    static net.minecraft.world.level.block.Block legacyTarget(String look) {
        String l = look.strip().toLowerCase(java.util.Locale.ROOT);
        if (l.startsWith("manholes:")) {
            l = l.substring("manholes:".length());
        }
        return switch (l) {
            case "hatch" -> ModRegistry.HATCH.get();
            case "grate" -> ModRegistry.GRATE.get();
            case "cave" -> ModRegistry.CAVE_HOLE.get();
            case "city" -> ModRegistry.CITY_MANHOLE.get();
            default -> null;
        };
    }

    /** Schedules the 1.3.0 look-to-block conversion (next server task; never during chunk loading). */
    private void scheduleLegacyConversion() {
        if (legacyLook.isEmpty() || !(level instanceof ServerLevel sl)) {
            return;
        }
        net.minecraft.server.MinecraftServer server = sl.getServer();
        server.tell(new net.minecraft.server.TickTask(server.getTickCount(), this::convertLegacyLook));
    }

    /** Swaps a (former {@code manholes:manhole}, now) {@code manholes:city_manhole} with a legacy {@code look} for the matching block. Returns true if swapped. */
    public boolean convertLegacyLook() {
        String l = legacyLook;
        legacyLook = "";
        if (l.isEmpty() || isRemoved() || !(level instanceof ServerLevel sl) || level.getBlockEntity(worldPosition) != this
                || !getBlockState().is(ModRegistry.CITY_MANHOLE.get())) {
            return false;
        }
        net.minecraft.world.level.block.Block target = legacyTarget(l);
        if (!(target instanceof ManholeBlock m)) {
            return false;
        }
        it.ratlab.manholes.Manholes.LOGGER.info("Converted legacy manhole look '{}' at {} {} to {}", l,
                sl.dimension().location(), worldPosition.toShortString(), m.id());
        return swapBlock(sl, worldPosition, m);
    }

    /**
     * Replaces the cover at pos with {@code target}, keeping facing, open, node id, name, origin and rust (so the node
     * stays in every network). Returns false if there's no cover at pos. Server only.
     */
    public static boolean swapBlock(ServerLevel level, BlockPos pos, ManholeBlock target) {
        BlockState old = level.getBlockState(pos);
        if (!(old.getBlock() instanceof ManholeBlock) || !(level.getBlockEntity(pos) instanceof ManholeBlockEntity be)) {
            return false;
        }
        if (old.is(target)) {
            return true;
        }
        be.ensureRegistered();
        CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
        BlockState state = target.defaultBlockState()
                .setValue(ManholeBlock.FACING, old.getValue(ManholeBlock.FACING))
                .setValue(ManholeBlock.OPEN, old.getValue(ManholeBlock.OPEN));
        ManholeBlock.swapping = true;
        try {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        } finally {
            ManholeBlock.swapping = false;
        }
        if (!(level.getBlockEntity(pos) instanceof ManholeBlockEntity fresh)) {
            return false;
        }
        fresh.loadWithComponents(tag, level.registryAccess());
        fresh.setChanged();
        fresh.ensureRegistered();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
        return true;
    }

    /** Client: the open state the renderer shows, and when its animation started. */
    @SuppressWarnings("deprecation")
    @Override
    public void setBlockState(BlockState state) {
        BlockState before = getBlockState();
        super.setBlockState(state);
        if (level != null && level.isClientSide && state.hasProperty(ManholeBlock.OPEN) && before.hasProperty(ManholeBlock.OPEN)
                && before.getValue(ManholeBlock.OPEN) != state.getValue(ManholeBlock.OPEN)) {
            animStart = level.getGameTime();
            animating = clientAnimStarted != null && clientAnimStarted.test(this);
            // 1.7.2: the chunk section is rebuilt for the state change anyway; refresh the model data first and ask
            // for an immediate rebuild so the static cover leaves the mesh when the animation starts.
            refreshMesh(true);
        }
    }

    public boolean isHome() {
        return getBlockState().getBlock() instanceof ManholeBlock b && b.isHome();
    }

    /** Sets the name (raw string) and updates the registry. */
    public void setName(String raw) {
        this.name = raw == null ? "" : raw;
        setChanged();
        syncRecord();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
    }

    /** Marks this manhole as generated by a spawn rule. Name is only applied if the NBT didn't set one. */
    public void setOrigin(String ruleId, String structureId, @Nullable String ruleName) {
        this.ruleId = ruleId == null ? "" : ruleId;
        this.structureId = structureId == null ? "" : structureId;
        this.generated = true;
        if (name.isEmpty() && ruleName != null) {
            name = ruleName;
        }
        setChanged();
        syncRecord();
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId == null ? "" : ruleId;
        setChanged();
        syncRecord();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel && !ManholeBlock.swapping) {
            ensureRegistered();
            scheduleLegacyConversion();
        }
    }

    /**
     * Makes sure this manhole has a node id and is in the global registry. A node id already used at another
     * position (e.g. copied from a structure template) is replaced by a fresh one. Server only.
     */
    @Nullable
    public NodeRecord ensureRegistered() {
        if (!(level instanceof ServerLevel sl)) {
            return null;
        }
        ManholeData data = ManholeData.get(sl.getServer());
        ResourceKey<net.minecraft.world.level.Level> dim = sl.dimension();
        if (nodeId != null) {
            NodeRecord existing = data.node(nodeId);
            if (existing != null && (!existing.dimension.equals(dim) || !existing.pos.equals(worldPosition))) {
                nodeId = null;
            }
        }
        if (nodeId == null) {
            nodeId = UUID.randomUUID();
            setChanged();
        }
        if (rust < 0) {
            rust = isHome() ? 0 : rustFor(nodeId);
            setChanged();
            syncToClients();
        }
        NodeRecord r = data.putNode(nodeId, dim, worldPosition);
        fill(r, sl);
        data.setDirty();
        return r;
    }

    private void syncRecord() {
        if (level instanceof ServerLevel sl && nodeId != null) {
            NodeRecord r = ManholeData.get(sl.getServer()).node(nodeId);
            if (r == null) {
                ensureRegistered();
            } else {
                fill(r, sl);
                ManholeData.get(sl.getServer()).setDirty();
            }
        }
    }

    private void fill(NodeRecord r, ServerLevel sl) {
        r.name = name;
        r.ruleId = ruleId;
        r.structureId = structureId;
        r.generated = generated;
        r.home = isHome();
        r.look = look();
        if (r.home) {
            // The node is the authority for ownership (set by migration / commands while unloaded); the block entity
            // keeps a copy, which fills a node that lost it.
            if (r.owner == null && owner != null) {
                r.owner = owner;
                r.ownerName = ownerName;
                r.shared = shared;
            } else if (r.owner != null && (!r.owner.equals(owner) || r.shared != shared || !r.ownerName.equals(ownerName))) {
                owner = r.owner;
                ownerName = r.ownerName;
                shared = r.shared;
                setChanged();
            } else if (r.owner == null) {
                r.shared = true; // unowned homes count as shared
            }
        }
        if (r.fallback.isEmpty()) {
            Holder<Biome> biome = sl.getBiome(worldPosition);
            Component biomeName = biome.unwrapKey()
                    .<Component>map(k -> Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()))
                    .orElse(Component.translatable("manholes.name.unknown_biome"));
            Component fb = Component.translatable("manholes.name.fallback", biomeName, worldPosition.getX(), worldPosition.getZ());
            r.fallback = Names.toRaw(fb, sl.registryAccess());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nodeId = tag.hasUUID("node_id") ? tag.getUUID("node_id") : null;
        name = tag.getString("name");
        ruleId = tag.getString("rule");
        structureId = tag.getString("structure");
        generated = tag.getBoolean("generated");
        rust = tag.contains("rust") ? Math.max(0, Math.min(3, tag.getInt("rust"))) : -1;
        legacyLook = tag.getString("look");
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        shared = tag.getBoolean("shared");
        // Edited in place (e.g. /data merge): keep the registry in sync.
        if (level instanceof ServerLevel && nodeId != null) {
            syncRecord();
            scheduleLegacyConversion();
            syncToClients();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (nodeId != null) {
            tag.putUUID("node_id", nodeId);
        }
        if (!name.isEmpty()) {
            tag.putString("name", name);
        }
        if (!ruleId.isEmpty()) {
            tag.putString("rule", ruleId);
        }
        if (!structureId.isEmpty()) {
            tag.putString("structure", structureId);
        }
        if (generated) {
            tag.putBoolean("generated", true);
        }
        if (rust >= 0) {
            tag.putInt("rust", rust);
        }
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("owner_name", ownerName);
        }
        if (shared) {
            tag.putBoolean("shared", true);
        }
    }

    // A renamed home manhole keeps its name as the item's custom name, and vice versa.
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        Component custom = input.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            HolderLookup.Provider regs = level != null ? level.registryAccess() : RegistryAccess.EMPTY;
            name = Names.toRaw(custom, regs);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (!name.isEmpty() && isHome()) {
            HolderLookup.Provider regs = level != null ? level.registryAccess() : RegistryAccess.EMPTY;
            builder.set(DataComponents.CUSTOM_NAME, Names.parse(name, regs));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("name");
        tag.remove("owner");
        tag.remove("owner_name");
        tag.remove("shared");
    }
}
