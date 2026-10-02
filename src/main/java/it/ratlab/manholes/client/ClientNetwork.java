// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.net.NetworkSyncPayload;
import it.ratlab.manholes.net.OpenTravelScreenPayload.Entry;
import java.util.List;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;

/** The client's copy of its network (from {@link NetworkSyncPayload}); feeds the FTB Chunks map icons. */
public final class ClientNetwork {
    private static volatile List<Entry> nodes = List.of();
    /** Bumped on every sync (an open travel screen picks up renames from here). */
    private static volatile int version;
    /** Called after every change (FTB Chunks refreshes its icons here); a no-op without FTB Chunks. */
    public static Runnable onChange = () -> {};

    private ClientNetwork() {}

    static void init() {
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            nodes = List.of();
            onChange.run();
        });
    }

    public static List<Entry> nodes() {
        return nodes;
    }

    public static int version() {
        return version;
    }

    static void onSync(NetworkSyncPayload p) {
        nodes = List.copyOf(p.nodes());
        version++;
        onChange.run();
    }
}
