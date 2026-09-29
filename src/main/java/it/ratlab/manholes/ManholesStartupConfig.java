// SPDX-License-Identifier: MIT
package it.ratlab.manholes;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Startup config: {@code config/manholes-startup.toml}. FML loads a STARTUP config as soon as it is registered (in the
 * mod constructor, before items are registered), so item properties can read it. Changes need a game restart.
 */
public final class ManholesStartupConfig {
    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    static { B.push("crowbar"); }
    public static final ModConfigSpec.IntValue CROWBAR_DURABILITY = B
            .comment("Durability of manholes:crowbar. Read when items are registered: restart the game after changing it.")
            .defineInRange("crowbarDurability", 250, 1, 100_000);
    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();

    private ManholesStartupConfig() {}

    public static int crowbarDurability() {
        return SPEC.isLoaded() ? CROWBAR_DURABILITY.getAsInt() : CROWBAR_DURABILITY.getDefault();
    }
}
