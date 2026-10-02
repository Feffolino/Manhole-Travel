// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import it.ratlab.manholes.compat.TeamHooks;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** FTB Teams: a network belongs to the player's current team (party team, or their own player team). */
public final class FTBTeamsHooksImpl implements TeamHooks {
    private static Optional<TeamManager> manager() {
        try {
            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            return api != null && api.isManagerLoaded() ? Optional.of(api.getManager()) : Optional.empty();
        } catch (Throwable t) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> teamOf(ServerPlayer player) {
        return manager().flatMap(m -> m.getTeamForPlayer(player)).map(Team::getId);
    }

    @Override
    public Optional<UUID> teamOf(MinecraftServer server, UUID playerId) {
        return manager().flatMap(m -> m.getTeamForPlayerID(playerId)).map(Team::getId);
    }

    @Override
    public Optional<Component> teamName(MinecraftServer server, UUID teamId) {
        return manager().flatMap(m -> m.getTeamByID(teamId)).map(Team::getName);
    }

    @Override
    public Collection<UUID> members(MinecraftServer server, UUID teamId) {
        return manager().flatMap(m -> m.getTeamByID(teamId)).<Collection<UUID>>map(t -> List.copyOf(t.getMembers())).orElse(List.of());
    }

    @Override
    public Collection<ServerPlayer> onlineMembers(MinecraftServer server, UUID teamId) {
        return manager().flatMap(m -> m.getTeamByID(teamId)).<Collection<ServerPlayer>>map(Team::getOnlineMembers).orElse(List.of());
    }
}
