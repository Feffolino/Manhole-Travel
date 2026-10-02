// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.travel.TravelContext;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** KubeJS bridge. Without KubeJS nothing is posted and stages fall back to scoreboard tags. */
public interface KubeHooks {
    KubeHooks NONE = new KubeHooks() {};

    default boolean pried(ServerPlayer player, NodeRecord node, UUID owner) {
        return true;
    }

    default boolean pryPhase(ServerPlayer player, NodeRecord node, String phase, int rust) {
        return true;
    }

    default float mash(ServerPlayer player, NodeRecord node, float progress, int presses, float amount) {
        return amount;
    }

    default boolean travel(TravelContext ctx) {
        return true;
    }

    default boolean arrived(ServerPlayer player, NodeRecord node) {
        return true;
    }

    default boolean addStage(ServerPlayer player, String stage) {
        return false;
    }

    default boolean removeStage(ServerPlayer player, String stage) {
        return false;
    }
}
