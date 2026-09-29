// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * The per-look wording of a cover's condition (1.5.0): the internal rust level 0-3 is the same everywhere, but a wooden
 * hatch swells, a cave hole is choked with rubble and iron rusts. Keys {@code manholes.condition.<look>} (label) and
 * {@code manholes.condition.<look>.<0-3>} (level); a look without them falls back to {@code manholes.hud.rust.<level>}.
 * Home manholes show nothing (always 0).
 */
public final class ConditionText {
    private ConditionText() {}

    @Nullable
    public static Component of(String look, int rust) {
        if (look == null || look.equals("home_manhole") || look.equals("manholes:home_manhole")) {
            return null;
        }
        int level = Mth.clamp(rust, 0, 3);
        String l = look.startsWith("manholes:") ? look.substring("manholes:".length()) : look;
        String key = "manholes.condition." + l.replace(':', '.');
        Language lang = Language.getInstance();
        if (lang.has(key) && lang.has(key + "." + level)) {
            return Component.translatable("manholes.condition.format", Component.translatable(key), Component.translatable(key + "." + level));
        }
        return Component.translatable("manholes.hud.rust." + level);
    }
}
