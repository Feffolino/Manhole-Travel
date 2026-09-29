// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who sees and uses which node (1.5.0).
 * <ul>
 *   <li>World covers: the nodes in the player's team network (pried open by the team).</li>
 *   <li>Home manholes with an owner: personal. The owner always; with "share with team" on, also the members of the
 *       owner's <b>current</b> FTB team. Without FTB Teams there are no teams, so shared = owner only.</li>
 *   <li>Unowned homes (pre-1.5.0 saves that couldn't be attributed, NBT structures, API placements): shared, visible
 *       to the networks that had them (legacy membership); the first player who uses one claims it.</li>
 * </ul>
 */
public final class Access {
    private Access() {}

    public static boolean isOwner(ServerPlayer player, NodeRecord r) {
        return r.owner != null && r.owner.equals(player.getUUID());
    }

    /** Owner, or an operator (permission level 2). */
    public static boolean canManage(ServerPlayer player, NodeRecord r) {
        return isOwner(player, r) || player.hasPermissions(2);
    }

    /** Effective sharing: unowned homes always count as shared. */
    public static boolean isShared(NodeRecord r) {
        return r.owner == null || r.shared;
    }

    /** True if both players are in the same FTB team right now (always false without FTB Teams). */
    public static boolean sameTeam(MinecraftServer server, ServerPlayer viewer, UUID other) {
        Optional<UUID> a = Hooks.teams().teamOf(viewer);
        if (a.isEmpty()) {
            return false;
        }
        Optional<UUID> b = Hooks.teams().teamOf(server, other);
        return b.isPresent() && a.get().equals(b.get());
    }

    public static boolean canSee(ServerPlayer player, NodeRecord r) {
        if (!r.home || r.owner == null) {
            return ManholeData.get(player.server).isOpen(Owners.ownerOf(player), r.id);
        }
        return isOwner(player, r) || (r.shared && sameTeam(player.server, player, r.owner));
    }

    /** Every node the player can see and travel to: the team network plus the visible home manholes. */
    public static List<NodeRecord> visible(ServerPlayer player) {
        ManholeData data = ManholeData.get(player.server);
        Set<NodeRecord> out = new LinkedHashSet<>();
        for (NodeRecord r : data.network(Owners.ownerOf(player))) {
            if (!r.home || r.owner == null) {
                out.add(r);
            }
        }
        for (NodeRecord r : data.nodes()) {
            if (r.home && r.owner != null && canSee(player, r)) {
                out.add(r);
            }
        }
        return new ArrayList<>(out);
    }
}
