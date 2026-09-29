// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.jetbrains.annotations.Nullable;

/** Personal home manholes (1.5.0): ownership, sharing, claiming, the break rule and the old-save migration. */
public final class HomeManholes {
    private HomeManholes() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(HomeManholes::onBreak);
        bus.addListener((ServerStartedEvent e) -> {
            ManholeData data = ManholeData.get(e.getServer());
            if (data.homesNeedMigration()) {
                data.migrateLegacyHomes(e.getServer());
            }
        });
    }

    /** Server-side authority for the break rule (the client-side destroy progress is only cosmetic). */
    private static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof ManholeBlock b && b.isHome()
                && !ManholeBlock.mayBreakHome(event.getPlayer(), event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getPlayer() instanceof ServerPlayer sp && event.getLevel().getBlockEntity(event.getPos()) instanceof ManholeBlockEntity be) {
                sp.displayClientMessage(Component.translatable("manholes.message.home_not_yours", ownerLabel(be.ownerName())), true);
            }
        }
    }

    static Component ownerLabel(String name) {
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

    /** Sets owner / share on the node and its block entity (if loaded). */
    private static void apply(MinecraftServer server, NodeRecord r, @Nullable java.util.UUID owner, String name, boolean shared) {
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

    /** An unowned home becomes the player's; it stays shared, as it was. Returns true if claimed. */
    public static boolean claim(ServerPlayer player, NodeRecord r) {
        if (!r.home || r.owner != null) {
            return false;
        }
        apply(player.server, r, player.getUUID(), player.getGameProfile().getName(), true);
        player.displayClientMessage(Component.translatable("manholes.message.home_claimed"), true);
        return true;
    }

    /** Keeps the stored owner name fresh when the owner uses his home. */
    static void refreshOwnerName(ServerPlayer player, NodeRecord r) {
        if (r.home && Access.isOwner(player, r) && !player.getGameProfile().getName().equals(r.ownerName)) {
            apply(player.server, r, r.owner, player.getGameProfile().getName(), r.shared);
        }
    }

    /**
     * Server-authoritative share toggle. {@code actor} null = trusted (script / console). A player must own the home or
     * be an operator. Returns true if the home now has the requested value.
     */
    public static boolean setShared(MinecraftServer server, @Nullable ServerPlayer actor, NodeRecord r, boolean shared) {
        if (!r.home) {
            return false;
        }
        if (actor != null && !Access.canManage(actor, r)) {
            actor.displayClientMessage(Component.translatable("manholes.message.home_not_yours", ownerLabel(r.ownerName)), true);
            return false;
        }
        // (an unowned home keeps counting as shared whatever the flag says, until someone claims it)
        apply(server, r, r.owner, r.ownerName, shared);
        if (actor != null) {
            actor.displayClientMessage(Component.translatable(shared ? "manholes.message.home_shared" : "manholes.message.home_private"), true);
        }
        return true;
    }
}
