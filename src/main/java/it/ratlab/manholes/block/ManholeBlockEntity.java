// SPDX-License-Identifier: MIT
package it.ratlab.manholes.block;

import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.Names;
import it.ratlab.manholes.data.NodeRecord;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the stable node id and optional name of a manhole.
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

    // Client-side animation state (never saved).
    public long animStart = Long.MIN_VALUE;
    @Nullable
    public static java.util.function.Consumer<ManholeBlockEntity> clientAnimStarted;

    public ManholeBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.MANHOLE_BE.get(), pos, state);
    }

    @Nullable
    public UUID nodeId() {
        if (nodeId == null && level instanceof ServerLevel && !ManholeBlock.swapping) {
            ensureRegistered();
        }
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

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("rust", rust());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        if (tag.contains("rust")) {
            rust = Math.max(0, Math.min(3, tag.getInt("rust")));
        }
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            handleUpdateTag(tag);
        }
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

    public void setShared(boolean shared) {
        setOwner(owner, ownerName, shared);
    }

    public String look() {
        return getBlockState().getBlock() instanceof ManholeBlock b ? b.look() : "city";
    }

    public static boolean swapBlock(ServerLevel level, BlockPos pos, ManholeBlock target) {
        BlockState old = level.getBlockState(pos);
        if (!(old.getBlock() instanceof ManholeBlock) || !(level.getBlockEntity(pos) instanceof ManholeBlockEntity be)) {
            return false;
        }
        if (old.is(target)) {
            return true;
        }
        be.ensureRegistered();
        CompoundTag tag = be.saveWithoutMetadata();
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
        fresh.load(tag);
        fresh.setChanged();
        fresh.ensureRegistered();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void setBlockState(BlockState state) {
        BlockState before = getBlockState();
        super.setBlockState(state);
        if (level != null && level.isClientSide && state.hasProperty(ManholeBlock.OPEN) && before.hasProperty(ManholeBlock.OPEN)
                && before.getValue(ManholeBlock.OPEN) != state.getValue(ManholeBlock.OPEN)) {
            animStart = level.getGameTime();
            if (clientAnimStarted != null) {
                clientAnimStarted.accept(this);
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(getBlockPos()).inflate(1.0, 0.5, 1.0).expandTowards(0, 0.5, 0);
    }

    public boolean isHome() {
        return getBlockState().getBlock() instanceof ManholeBlock b && b.isHome();
    }

    public void setName(String raw) {
        this.name = raw == null ? "" : raw;
        setChanged();
        syncRecord();
        it.ratlab.manholes.travel.NetworkSync.markDirty();
    }

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
        }
    }

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
                r.shared = true;
            }
        }
        if (r.fallback.isEmpty()) {
            Holder<Biome> biome = sl.getBiome(worldPosition);
            Component biomeName = biome.unwrapKey()
                    .<Component>map(k -> Component.translatable("biome." + k.location().getNamespace() + "." + k.location().getPath()))
                    .orElse(Component.translatable("manholes.name.unknown_biome"));
            Component fb = Component.translatable("manholes.name.fallback", biomeName, worldPosition.getX(), worldPosition.getZ());
            r.fallback = Names.toRaw(fb);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        nodeId = tag.hasUUID("node_id") ? tag.getUUID("node_id") : null;
        name = tag.getString("name");
        if (name.isEmpty() && tag.contains("CustomName", 8)) {
            name = tag.getString("CustomName");
        }
        ruleId = tag.getString("rule");
        structureId = tag.getString("structure");
        generated = tag.getBoolean("generated");
        rust = tag.contains("rust") ? Math.max(0, Math.min(3, tag.getInt("rust"))) : -1;
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("owner_name");
        shared = tag.getBoolean("shared");
        if (level instanceof ServerLevel && nodeId != null) {
            syncRecord();
            syncToClients();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (nodeId != null) {
            tag.putUUID("node_id", nodeId);
        }
        if (!name.isEmpty()) {
            tag.putString("name", name);
            if (isHome()) {
                tag.putString("CustomName", name);
            }
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
}
