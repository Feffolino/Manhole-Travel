// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.net.TravelAnimPayload;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The climb down / up. Only the camera moves: the server has already put the player where he belongs (on the cover
 * for the descent, on the landing spot for the ascent) and keeps him there, so nothing here is trusted.
 * <p>
 * Angles come from {@link ViewportEvent.ComputeCameraAngles}. The position is set in {@link ViewportEvent.ComputeFov}
 * (fired by {@code GameRenderer.getFov} right after {@code Camera.setup}, before anything reads the camera position),
 * through an access transformer on {@code Camera.setPosition(Vec3)}.
 */
public final class TravelCamera {
    private static final double CLIMB = 1.5;
    private static final float LOOK_DOWN = 80f;
    private static final float LOOK_UP = -70f;

    private static TravelAnimPayload anim;
    private static int age;
    private static float eyeHeight = 1.62f;

    private TravelCamera() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> tick());
        NeoForge.EVENT_BUS.addListener(TravelCamera::onAngles);
        NeoForge.EVENT_BUS.addListener(TravelCamera::onFov);
        NeoForge.EVENT_BUS.addListener((RenderHandEvent e) -> {
            if (anim != null) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((RenderHighlightEvent.Block e) -> {
            if (anim != null) {
                e.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            stop();
            FadeOverlay.reset();
        });
    }

    public static boolean active() {
        return anim != null;
    }

    static void onPayload(TravelAnimPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            eyeHeight = mc.player.getEyeHeight();
        }
        anim = p;
        age = 0;
        if (p.kind() == TravelAnimPayload.DESCENT) {
            int half = p.ticks() / 2;
            FadeOverlay.startAnimated(half, p.ticks() - half, p.holdTicks());
        } else {
            FadeOverlay.fadeIn(Math.max(1, p.ticks() / 2));
        }
    }

    static void stop() {
        anim = null;
    }

    private static void tick() {
        if (anim == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            stop();
            return;
        }
        age++;
        boolean descent = anim.kind() == TravelAnimPayload.DESCENT;
        int t = anim.ticks();
        boolean climbing = descent ? age >= t / 2 && age <= t : age <= t * 2 / 3;
        if (climbing && age % 5 == 0) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(ModRegistry.SOUND_LADDER.get(),
                    0.9f + mc.level.random.nextFloat() * 0.2f, 0.7f));
        }
        if (!descent && age >= t) {
            LocalPlayer p = mc.player;
            p.setYRot(anim.yaw());
            p.setXRot(0f);
            p.yRotO = anim.yaw();
            p.xRotO = 0f;
            stop();
        }
        // A descent just holds at the bottom (black) until the ascent or FadeOverlay's safety timeout.
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private record Pose(Vec3 eye, float yaw, float pitch) {}

    private static Pose pose(float pt) {
        TravelAnimPayload a = anim;
        float time = age + pt;
        Vec3 up = new Vec3(0, eyeHeight, 0);
        Vec3 coverEye = a.cover().add(up);
        if (a.kind() == TravelAnimPayload.DESCENT) {
            float q = a.ticks() / 4f;
            if (time < q) {
                float s = smooth(time / q);
                Vec3 from = a.from().add(up);
                return new Pose(from.lerp(coverEye, s), Mth.rotLerp(s, a.fromYaw(), a.yaw()), a.fromPitch());
            }
            if (time < 2 * q) {
                return new Pose(coverEye, a.yaw(), Mth.lerp(smooth((time - q) / q), a.fromPitch(), LOOK_DOWN));
            }
            float k = smooth((time - 2 * q) / Math.max(1f, a.ticks() - 2 * q));
            return new Pose(coverEye.subtract(0, CLIMB * k, 0), a.yaw(), LOOK_DOWN);
        }
        float r = a.ticks() * 2f / 3f;
        if (time < r) {
            float k = smooth(time / r);
            return new Pose(coverEye.subtract(0, CLIMB * (1 - k), 0), a.yaw(), LOOK_UP);
        }
        float k = smooth((time - r) / Math.max(1f, a.ticks() - r));
        return new Pose(coverEye.lerp(a.to().add(up), k), a.yaw(), Mth.lerp(k, LOOK_UP, 0f));
    }

    private static void onAngles(ViewportEvent.ComputeCameraAngles e) {
        if (anim == null) {
            return;
        }
        Pose p = pose((float) e.getPartialTick());
        e.setYaw(p.yaw());
        e.setPitch(p.pitch());
        e.setRoll(0f);
    }

    private static void onFov(ViewportEvent.ComputeFov e) {
        if (anim == null || !e.usedConfiguredFov()) {
            return;
        }
        Camera camera = e.getCamera();
        if (camera.isDetached()) {
            return;
        }
        camera.setPosition(pose((float) e.getPartialTick()).eye());
    }
}
