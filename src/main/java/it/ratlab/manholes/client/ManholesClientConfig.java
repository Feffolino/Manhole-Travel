// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client config: {@code config/manholes-client.toml}. */
public final class ManholesClientConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();

    public enum MashKey { JUMP, ATTACK }

    public static final ForgeConfigSpec.EnumValue<MashKey> MASH_KEY = B
            .comment("Key you mash during the LEVER phase of prying (press it as often and as fast as you can).",
                    " JUMP (default): the jump key. Jumping is suppressed while you pry, so it can't cancel the attempt.",
                    " ATTACK: the attack key. Attacking / mining is suppressed while you pry.",
                    "Both follow whatever the key is bound to in Controls.")
            .defineEnum("mashKey", MashKey.JUMP);

    public static final ForgeConfigSpec.BooleanValue ANIMATE_COVERS = B
            .comment("Animate manhole covers opening and closing (the lid parts of the cover's look slide / swing aside).",
                    "false = covers snap to the open or closed state at once.")
            .define("animateCovers", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_CONDITION_OVERLAYS = B
            .comment("Draw the cover's condition (rust / swollen wood / rubble level 1-3) as a decal overlay on the lid.",
                    "false = covers always look like new.")
            .define("showConditionOverlays", true);

    public static final ForgeConfigSpec SPEC = B.build();

    private ManholesClientConfig() {}

    public static boolean animateCovers() {
        return SPEC.isLoaded() ? ANIMATE_COVERS.get() : ANIMATE_COVERS.getDefault();
    }

    public static boolean showConditionOverlays() {
        return SPEC.isLoaded() ? SHOW_CONDITION_OVERLAYS.get() : SHOW_CONDITION_OVERLAYS.getDefault();
    }

    public static MashKey key() {
        return SPEC.isLoaded() ? MASH_KEY.get() : MASH_KEY.getDefault();
    }
}
