// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.data.NodeRecord;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Who owns a network: the player's FTB team when FTB Teams is present, otherwise the player. */
public final class Owners {
    private Owners() {}

    public static UUID ownerOf(ServerPlayer player) {
        return Hooks.teams().teamOf(player).orElse(player.getUUID());
    }

    public static Component ownerName(MinecraftServer server, UUID owner, ServerPlayer fallback) {
        return Hooks.teams().teamName(server, owner).orElse(fallback.getName());
    }

    /** Online players sharing this owner's network. */
    public static Collection<ServerPlayer> onlineMembers(MinecraftServer server, UUID owner) {
        Collection<ServerPlayer> members = Hooks.teams().onlineMembers(server, owner);
        if (!members.isEmpty()) {
            return members;
        }
        List<ServerPlayer> out = new ArrayList<>();
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) {
            out.add(p);
        }
        return out;
    }

    /** Gives the "opened" stage of a node (KubeJS stage, or scoreboard tag without KubeJS). */
    public static void grantStage(ServerPlayer player, NodeRecord node) {
        String stage = node.stageName();
        if (!Hooks.kube().addStage(player, stage)) {
            player.addTag(stage);
        }
    }

    public static void revokeStage(ServerPlayer player, NodeRecord node) {
        String stage = node.stageName();
        if (!Hooks.kube().removeStage(player, stage)) {
            player.removeTag(stage);
        }
    }
}
