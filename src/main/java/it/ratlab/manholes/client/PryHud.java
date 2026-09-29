// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.net.MashPressPayload;
import it.ratlab.manholes.net.PryStatePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Client side of phased prying: a GUI layer (not a Screen, so the player can still look around) with the pry bar under
 * the crosshair, and the mash key. Everything shown comes from the server ({@link PryStatePayload}); the only thing
 * sent back is one {@link MashPressPayload} per real key press (key-repeat events don't count).
 */
public final class PryHud {
    /** Hide the HUD if the server stops talking (it sends a state every tick of a session). */
    private static final int STALE_TICKS = 10;
    /** Presses sent per client tick at most (the server caps them per second anyway). */
    private static final int MAX_SEND_PER_TICK = 4;
    private static final ResourceLocation SHEET = Manholes.id("textures/gui/pry_bar.png");
    private static final int SHEET_W = 256;
    private static final int SHEET_H = 64;
    private static final int FRAME_W = 200;
    private static final int FRAME_H = 20;
    private static final int FILL_X = 6;
    private static final int FILL_Y = 6;
    private static final int FILL_W = 188;
    private static final int FILL_H = 8;
    private static final int FLASH_TICKS = 3;
    private static final int SHAKE_TICKS = 2;
    /** Ticks of the crowbar "pump" after an accepted press. */
    static final float PUMP_TICKS = 5f;

    private static PryStatePayload state = PryStatePayload.NONE;
    private static int staleTicks;
    private static int lastPresses;
    private static int flashTicks;
    private static int shakeTicks;
    private static int shakeX;
    private static int shakeY;
    private static int pendingPresses;
    private static int clientTicks;
    /** Client tick of the last accepted press (for the crowbar pump), or -1000. */
    private static int pumpTick = -1000;

    private PryHud() {}

    static void init() {
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Pre e) -> tick());
        NeoForge.EVENT_BUS.addListener(PryHud::onInteraction);
        NeoForge.EVENT_BUS.addListener(PryHud::onMovementInput);
        NeoForge.EVENT_BUS.addListener(PryHud::onKey);
        NeoForge.EVENT_BUS.addListener(PryHud::onMouse);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> reset());
    }

    public static boolean active() {
        return state.phase() != 0;
    }

    static void onState(PryStatePayload p) {
        if (p.phase() != 0 && p.presses() > lastPresses && state.phase() != 0) {
            flashTicks = FLASH_TICKS;
            shakeTicks = SHAKE_TICKS;
            pumpTick = clientTicks;
        }
        lastPresses = p.phase() == 0 ? 0 : p.presses();
        state = p;
        staleTicks = 0;
    }

    private static void reset() {
        state = PryStatePayload.NONE;
        lastPresses = 0;
        pendingPresses = 0;
    }

    /** 0..1 strength of the lever pump right now (1 just after a press, back to 0 over PUMP_TICKS). */
    static float pump(float partialTick) {
        float t = clientTicks - pumpTick + partialTick;
        if (!active() || t < 0 || t > PUMP_TICKS) {
            return 0f;
        }
        float k = t / PUMP_TICKS;
        return k < 0.3f ? k / 0.3f : 1f - (k - 0.3f) / 0.7f; // quick push down, slower return
    }

    private static KeyMapping key() {
        Minecraft mc = Minecraft.getInstance();
        return ManholesClientConfig.key() == ManholesClientConfig.MashKey.ATTACK ? mc.options.keyAttack : mc.options.keyJump;
    }

    private static boolean mashing() {
        return state.phase() == 2 && Minecraft.getInstance().screen == null;
    }

    private static void drain(KeyMapping k) {
        while (k.consumeClick()) {
            // discard
        }
    }

    /** Real presses only (GLFW_PRESS): holding the key (repeat events) is not mashing. */
    private static void onKey(InputEvent.Key e) {
        if (e.getAction() == GLFW.GLFW_PRESS && mashing() && key().matches(e.getKey(), e.getScanCode())) {
            pendingPresses++;
        }
    }

    private static void onMouse(InputEvent.MouseButton.Pre e) {
        if (e.getAction() == GLFW.GLFW_PRESS && mashing() && key().matchesMouse(e.getButton())) {
            pendingPresses++;
        }
    }

    /** ClientTickEvent.Pre runs before vanilla handles key bindings, so the attack click never reaches startAttack. */
    private static void tick() {
        clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset();
            return;
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (shakeTicks > 0) {
            shakeTicks--;
            shakeX = mc.level.random.nextInt(5) - 2;
            shakeY = mc.level.random.nextInt(3) - 1;
        } else {
            shakeX = 0;
            shakeY = 0;
        }
        if (!active()) {
            pendingPresses = 0;
            return;
        }
        if (++staleTicks > STALE_TICKS) {
            reset();
            return;
        }
        // While prying neither jump nor attack clicks may reach vanilla (and old clicks must not count later).
        drain(mc.options.keyJump);
        drain(mc.options.keyAttack);
        int n = Math.min(pendingPresses, MAX_SEND_PER_TICK);
        pendingPresses = 0;
        if (state.phase() == 2) {
            for (int i = 0; i < n; i++) {
                PacketDistributor.sendToServer(MashPressPayload.INSTANCE);
            }
        }
    }

    /**
     * While prying: no attack, no mining, no swing. With a pry tool in hand, left-clicking a manhole never starts the
     * block-breaking progress or the swing either.
     */
    private static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean crowbarOnCover = mc.player != null && mc.level != null && it.ratlab.manholes.item.PryTools.isPryTool(mc.player.getMainHandItem())
                && mc.hitResult instanceof BlockHitResult bhr && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof ManholeBlock;
        if (active() || crowbarOnCover) {
            e.setCanceled(true);
            e.setSwingHand(false);
        }
    }

    private static void onMovementInput(MovementInputUpdateEvent e) {
        if (active()) {
            e.getInput().jumping = false;
        }
    }

    // ---------------------------------------------------------------- drawing

    /**
     * The pry tool actually held for this session (1.5.0), 16x16 centred in the 20x20 glyph cell at (x, y), in the HUD's
     * scaled pose. Nothing when the hand is empty; the sheet's crowbar glyph only if item rendering fails.
     */
    private static void drawTool(GuiGraphics g, Minecraft mc, int x, int y) {
        net.minecraft.world.item.ItemStack stack = mc.player == null ? net.minecraft.world.item.ItemStack.EMPTY
                : mc.player.getItemInHand(state.offHand() ? net.minecraft.world.InteractionHand.OFF_HAND
                        : net.minecraft.world.InteractionHand.MAIN_HAND);
        if (stack.isEmpty()) {
            return;
        }
        try {
            g.renderItem(stack, x + 2, y + 2);
        } catch (RuntimeException ex) {
            g.blit(SHEET, x, y, 200, 0, 20, 20, SHEET_W, SHEET_H);
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        if (!active()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        // 2x on small GUI scales so it's big and readable, unless the screen is too narrow for it.
        int scale = mc.getWindow().getGuiScale() <= 2 && g.guiWidth() >= (FRAME_W + 60) * 2 ? 2 : 1;
        int cx = g.guiWidth() / 2;
        int cy = g.guiHeight() / 2;

        g.pose().pushPose();
        // Everything below is laid out in "sheet pixels" around (0, 0) = top centre of the frame.
        g.pose().translate(cx + shakeX, cy + 30 + shakeY, 0);
        g.pose().scale(scale, scale, 1);
        int fx = -FRAME_W / 2;

        String phaseKey = switch (state.phase()) {
            case 1 -> "manholes.hud.phase.insert";
            case 2 -> "manholes.hud.phase.lever";
            default -> "manholes.hud.phase.slide";
        };
        g.drawCenteredString(font, Component.translatable(phaseKey).withStyle(ChatFormatting.BOLD), 0, -11, 0xFFF0E0C0);

        // Crowbar glyph, frame, fill (a horizontal crop of the strip, never stretched).
        drawTool(g, mc, fx - 22, 0);
        g.blit(SHEET, fx, 0, 0, 0, FRAME_W, FRAME_H, SHEET_W, SHEET_H);
        int filled = Mth.clamp(Math.round(FILL_W * Mth.clamp(state.progress(), 0, 1)), 0, FILL_W);
        int v = flashTicks > 0 ? 36 : state.danger() ? 48 : 24;
        if (filled > 0) {
            g.blit(SHEET, fx + FILL_X, FILL_Y, 0, v, filled, FILL_H, SHEET_W, SHEET_H);
        }

        // Rust label under the frame.
        int rustColor = switch (state.rust()) {
            case 0 -> 0xFF9A9A9A;
            case 1 -> 0xFFC08040;
            case 2 -> 0xFFB06020;
            default -> 0xFFD05020;
        };
        Component condition = ConditionText.of(state.look(), state.rust());
        if (condition != null) {
            g.drawCenteredString(font, condition, 0, FRAME_H + 3, rustColor);
        }

        // Key hint: "MASH [key]" during LEVER (pulsing key cap), "HOLD [use]" otherwise.
        boolean lever = state.phase() == 2;
        KeyMapping hintKey = lever ? key() : mc.options.keyUse;
        Component word = Component.translatable(lever ? "manholes.hud.mash" : "manholes.hud.hold").withStyle(ChatFormatting.BOLD);
        Component keyName = hintKey.getTranslatedKeyMessage();
        int capW = Math.max(16, font.width(keyName) + 8);
        int wordW = font.width(word);
        int total = wordW + 4 + capW;
        int hx = -total / 2;
        int hy = FRAME_H + 15;
        float time = clientTicks + pt;
        int bob = lever ? Math.round(Mth.sin(time * 0.9f) * 1.5f) : 0;
        g.drawString(font, word, hx, hy + 4, lever ? 0xFFFFD050 : 0xFFD0D0D0, true);
        int kx = hx + wordW + 4;
        int ky = hy + bob;
        // Key cap: 16x16 sprite, stretched in the middle for long key names.
        g.blit(SHEET, kx, ky, 224, 0, 5, 16, SHEET_W, SHEET_H);
        g.blit(SHEET, kx + 5, ky, capW - 10, 16, 229, 0, 6, 16, SHEET_W, SHEET_H);
        g.blit(SHEET, kx + capW - 5, ky, 235, 0, 5, 16, SHEET_W, SHEET_H);
        int keyColor = lever && flashTicks > 0 ? 0xFFFFFFFF : 0xFF303030;
        g.drawString(font, keyName, kx + (capW - font.width(keyName)) / 2, ky + 4, keyColor, false);
        g.pose().popPose();
    }
}
