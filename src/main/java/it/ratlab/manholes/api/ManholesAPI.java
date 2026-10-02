// SPDX-License-Identifier: MIT
package it.ratlab.manholes.api;

import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.travel.Owners;
import it.ratlab.manholes.travel.TravelHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** Server-side API used by commands, world generation and the KubeJS binding. */
public final class ManholesAPI {
    private ManholesAPI() {}

    // ---------------------------------------------------------------- nodes

    /** Parses a full node UUID or a unique prefix of one (at least 4 characters). */
    @Nullable
    public static NodeRecord resolve(MinecraftServer server, @Nullable String idOrPrefix) {
        if (idOrPrefix == null || idOrPrefix.isBlank()) {
            return null;
        }
        String s = idOrPrefix.trim().toLowerCase(Locale.ROOT);
        ManholeData data = ManholeData.get(server);
        try {
            NodeRecord r = data.node(UUID.fromString(s));
            if (r != null) {
                return r;
            }
        } catch (IllegalArgumentException ignored) {
            // not a full uuid: try a prefix
        }
        if (s.length() < 4) {
            return null;
        }
        NodeRecord found = null;
        for (NodeRecord r : data.nodes()) {
            if (r.id.toString().startsWith(s)) {
                if (found != null) {
                    return null; // ambiguous
                }
                found = r;
            }
        }
        return found;
    }

    /** Node of the manhole at pos (registering it if needed), or null if there's no manhole. */
    @Nullable
    public static NodeRecord nodeAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ManholeBlockEntity be) {
            return be.ensureRegistered();
        }
        return null;
    }

    /** Nearest node within {@code radius} blocks of pos in that level, or null. */
    @Nullable
    public static NodeRecord nearest(ServerLevel level, BlockPos pos, double radius) {
        NodeRecord best = null;
        double bestD = radius * radius;
        for (NodeRecord r : ManholeData.get(level.getServer()).nodes()) {
            if (r.dimension.equals(level.dimension())) {
                double d = r.pos.distSqr(pos);
                if (d <= bestD) {
                    bestD = d;
                    best = r;
                }
            }
        }
        return best;
    }

    /** True if the node's block is loaded and gone; such nodes are removed from the registry. */
    public static boolean pruneIfGone(MinecraftServer server, NodeRecord r) {
        ServerLevel level = server.getLevel(r.dimension);
        if (level == null) {
            ManholeData.get(server).removeNode(r.id);
            return true;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(r.pos.getX() >> 4, r.pos.getZ() >> 4);
        if (chunk == null) {
            return false;
        }
        if (!(chunk.getBlockEntity(r.pos) instanceof ManholeBlockEntity be) || !r.id.equals(be.nodeId())) {
            ManholeData.get(server).removeNode(r.id);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- placing

    /** Places a real network manhole ({@code manholes:city_manhole}, closed unless {@code open}). Returns its node, or null on failure. */
    @Nullable
    public static NodeRecord place(ServerLevel level, BlockPos pos, @Nullable String name, @Nullable String ruleId, boolean open,
            @Nullable Direction facing) {
        BlockState state = ModRegistry.CITY_MANHOLE.get().defaultBlockState()
                .setValue(ManholeBlock.OPEN, open)
                .setValue(ManholeBlock.FACING, facing == null ? Direction.NORTH : facing);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            if (!level.getBlockState(pos).is(ModRegistry.CITY_MANHOLE.get())) {
                return null;
            }
        }
        if (!(level.getBlockEntity(pos) instanceof ManholeBlockEntity be)) {
            return null;
        }
        if (name != null && !name.isEmpty()) {
            be.setName(name);
        }
        if (ruleId != null && !ruleId.isEmpty()) {
            be.setRuleId(ruleId);
        }
        return be.ensureRegistered();
    }

    /** Used by world generation: closed manhole, origin recorded, name only if the block entity has none. */
    public static void placeGenerated(ServerLevel level, BlockPos pos, Direction facing, String ruleId, @Nullable String structureId,
            @Nullable String name, @Nullable ManholeBlock block) {
        BlockState state = (block == null ? ModRegistry.CITY_MANHOLE.get() : block).defaultBlockState()
                .setValue(ManholeBlock.OPEN, false).setValue(ManholeBlock.FACING, facing);
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ManholeBlockEntity be) {
            be.ensureRegistered();
            be.setOrigin(ruleId, structureId, name);
        }
    }

    /**
     * Swaps the cover at pos (any cover block, loaded chunk) for another cover block, keeping facing, open, node id,
     * name and network membership. {@code blockId} is a block id ({@code manholes:hatch}) or a look id ({@code hatch},
     * {@code cave}). Returns false if there's no cover there or no cover block matches.
     */
    public static boolean setBlock(ServerLevel level, BlockPos pos, @Nullable String blockId) {
        ManholeBlock target = ManholeBlock.resolve(blockId);
        return target != null && level.isLoaded(pos) && ManholeBlockEntity.swapBlock(level, pos, target);
    }

    private static boolean setLookWarned;

    /** @deprecated since 1.4.0, looks are blocks: use {@link #setBlock}. "" keeps the current block. */
    @Deprecated
    public static boolean setLook(ServerLevel level, BlockPos pos, @Nullable String look) {
        if (!setLookWarned) {
            setLookWarned = true;
            it.ratlab.manholes.Manholes.LOGGER.warn("Manholes.setLook / ManholesAPI.setLook is deprecated since 1.4.0: "
                    + "every look is its own block now, use setBlock(level, pos, 'manholes:hatch')");
        }
        if (look == null || look.isBlank()) {
            return level.isLoaded(pos) && level.getBlockEntity(pos) instanceof ManholeBlockEntity;
        }
        return setBlock(level, pos, look);
    }

    /** Removes the manhole at pos. Returns true if there was one. */
    public static boolean remove(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).getBlock() instanceof ManholeBlock) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    public static boolean rename(MinecraftServer server, NodeRecord r, String rawName) {
        ServerLevel level = server.getLevel(r.dimension);
        if (level != null && level.getBlockEntity(r.pos) instanceof ManholeBlockEntity be && r.id.equals(be.nodeId())) {
            be.setName(rawName);
            return true;
        }
        r.name = rawName;
        ManholeData.get(server).setDirty();
        return true;
    }

    // ---------------------------------------------------------------- networks

    /** Per-owner alias of a node for the player's team ("" clears it; sanitised, max 32 chars). True if it changed. */
    public static boolean setAlias(ServerPlayer player, NodeRecord r, String name) {
        return ManholeData.get(player.server).setAlias(Owners.ownerOf(player), r.id,
                TravelHandler.sanitizeAlias(name));
    }

    /** True if the player can see / travel to the node: in his team network, or a home manhole visible to him. */
    public static boolean isOpen(ServerPlayer player, UUID nodeId) {
        NodeRecord r = ManholeData.get(player.server).node(nodeId);
        return r != null && it.ratlab.manholes.travel.Access.canSee(player, r);
    }

    /**
     * "Share with team" of the home manhole at pos (1.5.0). Trusted (scripts): no owner check. Returns false if there's
     * no home manhole there.
     */
    public static boolean setShared(ServerLevel level, BlockPos pos, boolean shared) {
        NodeRecord r = nodeAt(level, pos);
        return r != null && it.ratlab.manholes.travel.HomeManholes.setShared(level.getServer(), null, r, shared);
    }

    /** Adds the node to the player's team network, opens the cover and gives the stage to online members. */
    public static boolean open(ServerPlayer player, NodeRecord r) {
        UUID owner = Owners.ownerOf(player);
        boolean added = ManholeData.get(player.server).open(owner, r.id);
        ServerLevel level = player.server.getLevel(r.dimension);
        if (level != null && level.isLoaded(r.pos)) {
            BlockState s = level.getBlockState(r.pos);
            if (s.getBlock() instanceof ManholeBlock && !s.getValue(ManholeBlock.OPEN)) {
                level.setBlock(r.pos, s.setValue(ManholeBlock.OPEN, true), Block.UPDATE_ALL);
            }
        }
        for (ServerPlayer member : Owners.onlineMembers(player.server, owner)) {
            Owners.grantStage(member, r);
        }
        Owners.grantStage(player, r);
        return added;
    }

    /** Removes the node from the player's team network (the cover keeps its look). */
    public static boolean close(ServerPlayer player, NodeRecord r) {
        UUID owner = Owners.ownerOf(player);
        ManholeData data = ManholeData.get(player.server);
        boolean removed = data.close(owner, r.id);
        String stage = r.stageName();
        boolean stillHasStage = data.network(owner).stream().anyMatch(n -> n.stageName().equals(stage));
        if (removed && !stillHasStage) {
            for (ServerPlayer member : Owners.onlineMembers(player.server, owner)) {
                Owners.revokeStage(member, r);
            }
            Owners.revokeStage(player, r);
        }
        return removed;
    }

    /** What the player can travel to: his team network plus the home manholes visible to him. */
    public static List<NodeRecord> network(ServerPlayer player) {
        return new ArrayList<>(it.ratlab.manholes.travel.Access.visible(player));
    }

    /** Scripted / command travel: same fade, no cost, no membership or blocker checks. */
    public static boolean travel(ServerPlayer player, NodeRecord to) {
        return TravelHandler.startScripted(player, to);
    }
}
