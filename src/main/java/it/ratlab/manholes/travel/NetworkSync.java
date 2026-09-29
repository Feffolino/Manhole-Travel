// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.net.Net;
import it.ratlab.manholes.net.NetworkSyncPayload;
import it.ratlab.manholes.net.OpenTravelScreenPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Keeps each client's copy of its network (team network + visible home manholes, see {@link Access}) (used for the FTB Chunks map icons) up to date. Changes (open, close, node
 * removed or renamed) only set a flag; the lists go out once at the end of that server tick, to every online player.
 */
public final class NetworkSync {
    private static volatile boolean dirty;

    private NetworkSync() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener((ServerTickEvent.Post e) -> {
            if (dirty) {
                dirty = false;
                syncAll(e.getServer());
            }
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                sync(p);
            }
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer p) {
                sync(p);
            }
        });
    }

    public static void markDirty() {
        dirty = true;
    }

    public static void syncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sync(p);
        }
    }

    public static void sync(ServerPlayer player) {
        Net.send(player, payload(player));
    }

    /** The {@code network_sync} a player gets: every visible node, each with its cover's look (map icon per look). */
    public static NetworkSyncPayload payload(ServerPlayer player) {
        List<OpenTravelScreenPayload.Entry> out = new ArrayList<>();
        for (NodeRecord r : Access.visible(player)) {
            out.add(TravelHandler.entry(player, r));
        }
        return new NetworkSyncPayload(out);
    }
}
