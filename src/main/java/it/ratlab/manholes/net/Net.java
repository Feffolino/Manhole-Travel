// SPDX-License-Identifier: MIT
package it.ratlab.manholes.net;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

/** Sends only to real connections that negotiated our channel (fake players and game-test mock players don't). */
public final class Net {
    private Net() {}

    public static void send(ServerPlayer player, Object payload) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        try {
            ManholeNetworking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), payload);
        } catch (RuntimeException ignored) {
            // a connection without channel info (test harness): nothing to show anyway
        }
    }

    public static void sendToServer(Object payload) {
        ManholeNetworking.CHANNEL.sendToServer(payload);
    }
}
