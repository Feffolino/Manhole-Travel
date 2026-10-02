// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.data.NodeRecord;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** A requested trip, before costs are applied. Scripts may edit the costs or cancel it. */
public final class TravelContext {
    public final ServerPlayer player;
    @Nullable
    public final NodeRecord from;
    public final NodeRecord to;
    /** Food exhaustion to add. */
    public double hunger;
    /** World time to advance (only applied with exactly one player online). */
    public long timeTicks;

    public TravelContext(ServerPlayer player, @Nullable NodeRecord from, NodeRecord to, double hunger, long timeTicks) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.hunger = hunger;
        this.timeTicks = timeTicks;
    }
}
