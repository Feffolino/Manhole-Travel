// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Team provider. Without FTB Teams every player is their own team. */
public interface TeamHooks {
    TeamHooks NONE = new TeamHooks() {};

    default Optional<UUID> teamOf(ServerPlayer player) {
        return Optional.empty();
    }

    default Optional<UUID> teamOf(MinecraftServer server, UUID playerId) {
        return Optional.empty();
    }

    default Optional<Component> teamName(MinecraftServer server, UUID teamId) {
        return Optional.empty();
    }

    default Collection<UUID> members(MinecraftServer server, UUID teamId) {
        return List.of();
    }

    default Collection<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        return List.of();
    }
}
