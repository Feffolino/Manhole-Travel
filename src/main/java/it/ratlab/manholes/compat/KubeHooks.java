// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.gen.GenerateContext;
import it.ratlab.manholes.gen.SpawnRuleSet;
import it.ratlab.manholes.travel.TravelContext;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** KubeJS bridge. Without KubeJS nothing is posted and stages fall back to scoreboard tags. */
public interface KubeHooks {
    KubeHooks NONE = new KubeHooks() {};

    /** ManholeEvents.spawnRules: scripts may add, remove or modify rules. */
    default void spawnRules(SpawnRuleSet rules) {}

    /** ManholeEvents.generate. Returns false if a script cancelled it. */
    default boolean generate(GenerateContext ctx) {
        return true;
    }

    /** ManholeEvents.pried. Returns false if a script cancelled it. */
    default boolean pried(ServerPlayer player, NodeRecord node, UUID owner) {
        return true;
    }

    /** ManholeEvents.pryPhase: a phased pry session enters a phase (insert, lever, slide). false = cancelled. */
    default boolean pryPhase(ServerPlayer player, NodeRecord node, String phase, int rust) {
        return true;
    }

    /**
     * ManholeEvents.mash: an accepted press of the mash key during LEVER. {@code progress} is the bar before the press
     * (0..1), {@code presses} counts this one. Returns the (possibly script-changed) amount to add (fraction of the bar).
     */
    default float mash(ServerPlayer player, NodeRecord node, float progress, int presses, float amount) {
        return amount;
    }

    /** ManholeEvents.travel. Returns false if a script cancelled it; costs in ctx may be edited. */
    default boolean travel(TravelContext ctx) {
        return true;
    }

    /** ManholeEvents.arrived. Returns false if a script cancelled it (= skip the built-in ambush). */
    default boolean arrived(ServerPlayer player, NodeRecord node) {
        return true;
    }

    /** Adds a KubeJS stage. Returns false if KubeJS isn't there (caller falls back to a scoreboard tag). */
    default boolean addStage(ServerPlayer player, String stage) {
        return false;
    }

    default boolean removeStage(ServerPlayer player, String stage) {
        return false;
    }
}
