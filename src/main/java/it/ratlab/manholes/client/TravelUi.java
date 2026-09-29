// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Hides the whole HUD (hotbar, hand, crosshair, chat, FTB Chunks' minimap, which checks {@code hideGui} itself) while a
 * trip runs: from the start of the descent / fade until the ascent / fade-in is over. The player's own {@code hideGui}
 * (F1) is restored at the end, when the trip is cancelled and on disconnect. Our fade overlay is a mod GUI layer that
 * NeoForge draws regardless of {@code hideGui}.
 */
public final class TravelUi {
    private static boolean hiding;
    private static boolean previous;

    private TravelUi() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> update());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> restore());
    }

    public static boolean hiding() {
        return hiding;
    }

    /** Called when a trip starts (also from the payload handlers, so the first frame is already clean). */
    static void begin() {
        Minecraft mc = Minecraft.getInstance();
        if (!hiding) {
            previous = mc.options.hideGui;
            hiding = true;
        }
        mc.options.hideGui = true;
    }

    static void restore() {
        if (hiding) {
            hiding = false;
            Minecraft.getInstance().options.hideGui = previous;
        }
    }

    private static void update() {
        boolean travelling = Minecraft.getInstance().level != null && (FadeOverlay.active() || TravelCamera.active());
        if (travelling) {
            begin();
        } else {
            restore();
        }
    }
}
