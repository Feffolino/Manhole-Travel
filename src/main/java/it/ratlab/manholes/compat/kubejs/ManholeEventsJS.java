// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/** {@code ManholeEvents.*} (server scripts). */
public interface ManholeEventsJS {
    EventGroup GROUP = EventGroup.of("ManholeEvents");

    EventHandler SPAWN_RULES = GROUP.server("spawnRules", () -> ManholeKubeEvents.SpawnRules.class);
    EventHandler GENERATE = GROUP.server("generate", () -> ManholeKubeEvents.Generate.class).hasResult();
    EventHandler PRIED = GROUP.server("pried", () -> ManholeKubeEvents.Pried.class).hasResult();
    EventHandler PRY_PHASE = GROUP.server("pryPhase", () -> ManholeKubeEvents.PryPhase.class).hasResult();
    EventHandler MASH = GROUP.server("mash", () -> ManholeKubeEvents.Mash.class);
    /** Deprecated no-op since 1.2.0 (kept so 1.1 scripts still load; a warning is logged if a script uses it). */
    @Deprecated
    EventHandler SKILL_CHECK = GROUP.server("skillCheck", () -> ManholeKubeEvents.SkillCheck.class);
    EventHandler TRAVEL = GROUP.server("travel", () -> ManholeKubeEvents.Travel.class).hasResult();
    EventHandler ARRIVED = GROUP.server("arrived", () -> ManholeKubeEvents.Arrived.class).hasResult();
}
