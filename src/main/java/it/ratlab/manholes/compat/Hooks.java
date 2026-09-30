// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import it.ratlab.manholes.Manholes;

/**
 * Entry points of the optional integrations. The default implementations do nothing; the real ones live in
 * {@code compat.kubejs} / {@code compat.ftbteams} and are only class-loaded after a ModList check.
 */
public final class Hooks {
    private static KubeHooks kube = KubeHooks.NONE;
    private static TeamHooks teams = TeamHooks.NONE;
    private static ClaimHooks claims = ClaimHooks.NONE;

    private Hooks() {}

    public static KubeHooks kube() {
        return kube;
    }

    public static ClaimHooks claims() {
        return claims;
    }

    public static void enableFTBChunks() {
        try {
            claims = new it.ratlab.manholes.compat.ftbchunks.FTBChunksClaims();
            Manholes.LOGGER.info("FTB Chunks found: no ambushes at manholes in claimed chunks");
        } catch (Throwable t) {
            Manholes.LOGGER.error("FTB Chunks integration failed to load, claims are ignored", t);
        }
    }

    public static TeamHooks teams() {
        return teams;
    }

    public static void enableKubeJS() {
        try {
            kube = new it.ratlab.manholes.compat.kubejs.KubeJSHooksImpl();
            Manholes.LOGGER.info("KubeJS found: ManholeEvents and the Manholes binding are available");
        } catch (Throwable t) {
            Manholes.LOGGER.error("KubeJS integration failed to load, continuing without it", t);
        }
    }

    public static void enableFTBTeams() {
        try {
            teams = new it.ratlab.manholes.compat.ftbteams.FTBTeamsHooksImpl();
            Manholes.LOGGER.info("FTB Teams found: manhole networks belong to teams");
        } catch (Throwable t) {
            Manholes.LOGGER.error("FTB Teams integration failed to load, networks stay per player", t);
        }
    }
}
