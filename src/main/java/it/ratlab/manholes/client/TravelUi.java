// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;

/**
 * Hides all HUD overlays (hotbar, health, crosshair, chat, mod overlays, etc.) while a trip runs:
 * from the start of the descent / fade until the ascent / fade-in is over.
 * Cancels Forge GUI overlay events instead of toggling {@code mc.options.hideGui}, so Minecraft's
 * GUI render pass remains active and draws our fade overlay.
 */
public final class TravelUi {
    private static boolean hiding;

    private TravelUi() {}

    static void init() {
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.START) {
                update();
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> restore());
        MinecraftForge.EVENT_BUS.addListener((RenderGuiOverlayEvent.Pre e) -> {
            if (hiding && !e.getOverlay().id().getPath().equals("travel_fade")) {
                e.setCanceled(true);
            }
        });
    }

    public static boolean hiding() {
        return hiding;
    }

    /** Called when a trip starts (also from the payload handlers, so the first frame is already clean). */
    static void begin() {
        hiding = true;
    }

    static void restore() {
        hiding = false;
    }

    private static void update() {
        Minecraft mc = Minecraft.getInstance();
        boolean travelling = mc.level != null && (FadeOverlay.active() || TravelCamera.active());
        if (travelling) {
            begin();
        } else {
            restore();
        }
    }
}
