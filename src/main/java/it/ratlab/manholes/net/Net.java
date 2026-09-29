// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sends only to real connections that negotiated our channel (fake players and game-test mock players don't). */
public final class Net {
    private Net() {}

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        try {
            if (player.connection.hasChannel(payload)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        } catch (RuntimeException ignored) {
            // a connection without channel info (test harness): nothing to show anyway
        }
    }
}
