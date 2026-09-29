// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.ModRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * The trip: fade to black, echoing footsteps and dripping water, one random flavor line
 * ({@code manholes.travel.flavor.0..N} - add lines in a resource pack), then a fade back in after the teleport.
 * <p>
 * Plain mode ({@link #start}): fade over 60% of the server's fade time, fade in 20 ticks after it ends.
 * Animated mode ({@link #startAnimated}): black ramps up during the second half of the climb down and stays until the
 * climb up begins ({@link #fadeIn}); a safety timeout fades in if the server never answers.
 */
public final class FadeOverlay {
    private static final int FADE_IN_TICKS = 20;
    private static final int MAX_FLAVOR_LINES = 100;
    private static final int SAFETY_TICKS = 400;

    private static int total;
    private static int delay;
    private static int ramp;
    private static int footstepsFrom;
    private static boolean waitForServer;
    private static int elapsed = -1;
    private static int fadeIn;
    private static int fadeInTotal = FADE_IN_TICKS;
    private static Component line = Component.empty();

    private FadeOverlay() {}

    static void start(int ticks) {
        if (ticks <= 0) {
            cancel();
            return;
        }
        begin(ticks, 0, Math.max(1, (int) (ticks * 0.6f)), 0, false);
    }

    /** Black ramps up from {@code delay} over {@code rampTicks}, then holds until {@link #fadeIn}. */
    static void startAnimated(int delayTicks, int rampTicks, int holdTicks) {
        begin(delayTicks + rampTicks + holdTicks + SAFETY_TICKS, delayTicks, Math.max(1, rampTicks), delayTicks + rampTicks, true);
    }

    private static void begin(int totalTicks, int delayTicks, int rampTicks, int stepsFrom, boolean wait) {
        total = totalTicks;
        delay = delayTicks;
        ramp = rampTicks;
        footstepsFrom = stepsFrom;
        waitForServer = wait;
        elapsed = 0;
        fadeIn = 0;
        line = randomLine();
        TravelUi.begin();
    }

    /** Ends the black and fades back in over {@code ticks}. */
    static void fadeIn(int ticks) {
        elapsed = -1;
        fadeInTotal = Math.max(1, ticks);
        fadeIn = fadeInTotal;
    }

    static void cancel() {
        fadeIn(FADE_IN_TICKS);
    }

    /** Black is showing or fading (a trip is running). */
    static boolean active() {
        return elapsed >= 0 || fadeIn > 0;
    }

    static void reset() {
        elapsed = -1;
        fadeIn = 0;
    }

    /**
     * A random flavor line: {@code manholes.travel.flavor.<look>.N} for the look of the nearest cover (the one the
     * player climbs into), else the generic {@code manholes.travel.flavor.N}.
     */
    private static Component randomLine() {
        Minecraft mc = Minecraft.getInstance();
        String look = nearbyLook(mc);
        if (look != null) {
            Component c = pick("manholes.travel.flavor." + look.replace(':', '.') + ".", mc);
            if (c != null) {
                return c;
            }
        }
        Component c = pick("manholes.travel.flavor.", mc);
        return c == null ? Component.empty() : c;
    }

    @org.jetbrains.annotations.Nullable
    private static Component pick(String prefix, Minecraft mc) {
        int n = 0;
        while (n < MAX_FLAVOR_LINES && I18n.exists(prefix + n)) {
            n++;
        }
        return n == 0 ? null : Component.translatable(prefix + mc.level.random.nextInt(n));
    }

    /** Look of the nearest manhole within 8 blocks of the player (client block entities), or null. */
    @org.jetbrains.annotations.Nullable
    static String nearbyLook(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        net.minecraft.core.BlockPos at = mc.player.blockPosition();
        String best = null;
        double bestD = Double.MAX_VALUE;
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(at.offset(-8, -3, -8), at.offset(8, 2, 8))) {
            if (mc.level.getBlockEntity(p) instanceof it.ratlab.manholes.block.ManholeBlockEntity be) {
                double d = p.distSqr(at);
                if (d < bestD) {
                    bestD = d;
                    best = be.look();
                }
            }
        }
        return best;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            reset();
            return;
        }
        if (elapsed >= 0) {
            elapsed++;
            if (elapsed >= footstepsFrom && (elapsed - footstepsFrom) % 8 == 4) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModRegistry.SOUND_FOOTSTEP.get(),
                        0.8f + mc.level.random.nextFloat() * 0.2f, 0.5f));
            }
            if (elapsed >= footstepsFrom && mc.level.random.nextInt(14) == 0) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(ModRegistry.SOUND_DRIP.get(),
                        0.8f + mc.level.random.nextFloat() * 0.4f, 0.6f));
            }
            if (elapsed >= total) {
                if (waitForServer) {
                    TravelCamera.stop(); // the server never sent the arrival: give the view back
                }
                fadeIn(FADE_IN_TICKS);
            }
        } else if (fadeIn > 0) {
            fadeIn--;
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        float alpha;
        float pt = delta.getGameTimeDeltaPartialTick(true);
        if (elapsed >= 0) {
            alpha = Mth.clamp((elapsed - delay + pt) / ramp, 0, 1);
        } else if (fadeIn > 0) {
            alpha = Mth.clamp((fadeIn - pt) / fadeInTotal, 0, 1);
        } else {
            return;
        }
        if (alpha <= 0) {
            return;
        }
        int a = (int) (alpha * 255) << 24;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), a);
        if (alpha > 0.5f && elapsed >= 0 && !line.getString().isEmpty()) {
            int textAlpha = (int) (Mth.clamp((alpha - 0.5f) * 2, 0.05f, 1f) * 255);
            Minecraft mc = Minecraft.getInstance();
            g.drawCenteredString(mc.font, line, g.guiWidth() / 2, g.guiHeight() / 2 - 4, (textAlpha << 24) | 0xC8C8C8);
        }
    }
}
