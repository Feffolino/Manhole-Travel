// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat;

import it.ratlab.manholes.Manholes;

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

    public static TeamHooks teams() {
        return teams;
    }

    public static void enableFTBChunks() {
        // Wired in step 8
    }

    public static void enableKubeJS() {
        // Wired in step 8
    }

    public static void enableFTBTeams() {
        // Wired in step 8
    }
}
