// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ManholesConfig.Difficulty;

/**
 * The resolved numbers of one pry attempt: difficulty preset (or the custom keys) with rust applied.
 * {@code perPress}, {@code decay} and {@code giveUp} are fractions of the full LEVER bar (0..1).
 */
public record PryParams(boolean simple, int pryTicks, int insertTicks, int slideTicks, float perPress, float decay,
        int maxPressesPerSecond, int noiseEvery, float giveUp, int rust) {

    /** Built-in presets (see the config comment of prying.difficulty). Percent values. */
    private record Preset(int insert, int slide, double perPress, double decay) {}

    private static final Preset NORMAL = new Preset(20, 30, 6.0, 0.8);
    private static final Preset HARD = new Preset(30, 40, 4.5, 1.0);

    /** @param rust the node's rust level 0..3 (ignored when rustEnabled = false or in simple mode) */
    public static PryParams resolve(int rust) {
        Difficulty d = ManholesConfig.e(ManholesConfig.DIFFICULTY);
        if (d == Difficulty.SIMPLE) {
            return new PryParams(true, ManholesConfig.i(ManholesConfig.PRY_TICKS), 0, 0, 0, 0, 0, 1, 1, 0);
        }
        Preset p = switch (d) {
            case HARD -> HARD;
            case CUSTOM -> new Preset(ManholesConfig.i(ManholesConfig.INSERT_TICKS), ManholesConfig.i(ManholesConfig.SLIDE_TICKS),
                    ManholesConfig.d(ManholesConfig.MASH_PER_PRESS), ManholesConfig.d(ManholesConfig.MASH_DECAY_PER_TICK));
            default -> NORMAL;
        };
        int r = ManholesConfig.b(ManholesConfig.RUST_ENABLED) ? Math.max(0, Math.min(3, rust)) : 0;
        double factor = 1.0 + ManholesConfig.d(ManholesConfig.RUST_MULTIPLIER) * r;
        return new PryParams(false, 0, scale(p.insert, factor), scale(p.slide, factor),
                (float) (p.perPress / factor / 100.0), (float) (p.decay / 100.0),
                ManholesConfig.i(ManholesConfig.MASH_MAX_PRESSES_PER_SECOND), ManholesConfig.i(ManholesConfig.MASH_NOISE_EVERY),
                (float) (ManholesConfig.d(ManholesConfig.MASH_GIVE_UP_THRESHOLD) / 100.0), r);
    }

    private static int scale(int ticks, double factor) {
        return Math.max(1, (int) Math.round(ticks * factor));
    }
}
