// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.data.NodeRecord;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.IEventBus;

public final class TravelHandler {
    public static final double LANDING_SHIFT = 0.1;

    private TravelHandler() {}

    public static void registerEvents(IEventBus bus) {
    }

    public static String sanitizeAlias(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > 32 ? t.substring(0, 32) : t;
    }

    public static boolean startScripted(ServerPlayer player, NodeRecord to) {
        player.teleportTo(player.server.getLevel(to.dimension), to.pos.getX() + 0.5, to.pos.getY() + 1.0, to.pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }

    public static void openScreen(ServerPlayer player, NodeRecord node) {
    }
}
