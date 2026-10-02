// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

/** Personal home manholes (1.5.0): ownership, sharing, claiming, the break rule. */
public final class HomeManholes {
    private HomeManholes() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(HomeManholes::onBreak);
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof ManholeBlock b && b.isHome()
                && !ManholeBlock.mayBreakHome(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp && event.getLevel().getBlockEntity(event.getPos()) instanceof ManholeBlockEntity be) {
                sp.displayClientMessage(Component.translatable("manholes.message.home_not_yours", ownerLabel(be.ownerName())), true);
            }
        }
    }

    public static Component ownerLabel(String name) {
        return name == null || name.isEmpty() ? Component.translatable("manholes.home.owner_unknown") : Component.literal(name);
    }

    @Nullable
    private static ManholeBlockEntity blockEntity(MinecraftServer server, NodeRecord r) {
        ServerLevel level = server.getLevel(r.dimension);
        if (level != null && level.isLoaded(r.pos) && level.getBlockEntity(r.pos) instanceof ManholeBlockEntity be
                && r.id.equals(be.nodeId())) {
            return be;
        }
        return null;
    }

    private static void apply(MinecraftServer server, NodeRecord r, @Nullable UUID owner, String name, boolean shared) {
        ManholeBlockEntity be = blockEntity(server, r);
        if (be != null) {
            be.setOwner(owner, name, shared);
        } else {
            r.owner = owner;
            r.ownerName = name == null ? "" : name;
            r.shared = shared;
            ManholeData.get(server).setDirty();
            NetworkSync.markDirty();
        }
    }

    public static boolean claim(ServerPlayer player, NodeRecord r) {
        if (!r.home || r.owner != null) {
            return false;
        }
        apply(player.server, r, player.getUUID(), player.getGameProfile().getName(), true);
        player.displayClientMessage(Component.translatable("manholes.message.home_claimed"), true);
        return true;
    }

    public static void refreshOwnerName(ServerPlayer player, NodeRecord r) {
        if (r.home && Access.isOwner(player, r) && !player.getGameProfile().getName().equals(r.ownerName)) {
            apply(player.server, r, r.owner, player.getGameProfile().getName(), r.shared);
        }
    }

    public static boolean setShared(MinecraftServer server, @Nullable ServerPlayer actor, NodeRecord r, boolean shared) {
        if (!r.home) {
            return false;
        }
        if (actor != null && !Access.canManage(actor, r)) {
            actor.displayClientMessage(Component.translatable("manholes.message.home_not_yours", ownerLabel(r.ownerName)), true);
            return false;
        }
        apply(server, r, r.owner, r.ownerName, shared);
        return true;
    }
}
