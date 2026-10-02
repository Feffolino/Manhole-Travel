// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.net.Net;
import it.ratlab.manholes.net.PryStatePayload;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * Hold-right-click prying. A session exists only while a player is prying; it's ticked from the server tick and
 * refreshed by the repeated use packets the client sends while the use key is held (every 4 ticks).
 * <p>
 * {@code difficulty = simple}: hold for {@code pryTicks} (the 1.0 behaviour, action bar progress).
 * Otherwise three phases, all server-side: INSERT (hold), LEVER (mash the jump / attack key: every accepted press adds
 * {@code perPress}, the bar decays {@code decay} per tick without a press), SLIDE (hold). The server owns the session,
 * the phase and the bar, counts the presses ({@link it.ratlab.manholes.net.MashPressPayload}, capped per second) and
 * the client only draws what {@link PryStatePayload} says.
 */
public final class PryHandler {
    /** A session ends if no use packet arrived for this many ticks (= the button was released). */
    private static final int RELEASE_TIMEOUT = 8;
    /** Window of the press rate cap (1 second). */
    private static final int RATE_WINDOW = 20;
    /** Below this lever fraction a decaying bar is shown as "danger". */
    private static final float DANGER = 0.25f;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** After "the crowbar slips" the use key must be released before a new attempt: player -> last use tick. */
    private static final Map<UUID, Long> MUST_RELEASE = new HashMap<>();

    public enum Phase {
        INSERT, LEVER, SLIDE;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private static final class Session {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos pos;
        final UUID nodeId;
        final Vec3 start;
        final InteractionHand hand;
        final int slot;
        final Item item;
        final PryParams params;
        int ticks;
        long lastUse;
        // phased
        Phase phase = Phase.INSERT;
        int phaseTicks;
        /** LEVER bar, 0..1. */
        float lever;
        /** Highest lever value reached in this attempt (the give-up rule). */
        float peak;
        /** Accepted mash presses. */
        int presses;
        /** Presses dropped by the rate cap (tests / debugging). */
        int rejected;
        /** A press was accepted since the last tick (no decay this tick). */
        boolean pressed;
        /** Ticks since the last accepted press. */
        int idleTicks;
        /** Game times of the accepted presses of the last RATE_WINDOW ticks. */
        final ArrayDeque<Long> window = new ArrayDeque<>();

        Session(ServerPlayer player, ServerLevel level, BlockPos pos, UUID nodeId, InteractionHand hand, Item item,
                PryParams params, long now) {
            this.player = player;
            this.level = level;
            this.pos = pos;
            this.nodeId = nodeId;
            this.start = player.position();
            this.hand = hand;
            this.slot = player.getInventory().selected;
            this.item = item;
            this.params = params;
            this.lastUse = now;
        }
    }

    private PryHandler() {}

    public static void registerEvents(IEventBus bus) {
        bus.addListener(PryHandler::onServerTick);
        bus.addListener(PryHandler::onDamage);
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            SESSIONS.remove(e.getEntity().getUUID());
            MUST_RELEASE.remove(e.getEntity().getUUID());
        });
        bus.addListener((ServerStoppedEvent e) -> {
            SESSIONS.clear();
            MUST_RELEASE.clear();
        });
    }

    public static boolean isPrying(Player player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** True after an abort until the player lets go of the use key. */
    public static boolean mustRelease(Player player) {
        return MUST_RELEASE.containsKey(player.getUUID());
    }

    /** Rust level of the manhole at pos (0 for home manholes or without a block entity). */
    public static int rustAt(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ManholeBlockEntity be ? be.rust() : 0;
    }

    /** Called for every use packet on a closed (for this team) manhole with a pry tool. */
    public static void pry(ServerPlayer player, ServerLevel level, BlockPos pos, NodeRecord node, InteractionHand hand, ItemStack stack) {
        long now = level.getGameTime();
        Long released = MUST_RELEASE.get(player.getUUID());
        if (released != null) {
            if (now - released <= RELEASE_TIMEOUT) {
                MUST_RELEASE.put(player.getUUID(), now); // still held since the abort
                return;
            }
            MUST_RELEASE.remove(player.getUUID());
        }
        Session s = SESSIONS.get(player.getUUID());
        if (s != null && s.pos.equals(pos) && s.level == level) {
            s.lastUse = now;
            return;
        }
        PryParams params = PryParams.resolve(rustAt(level, pos));
        Session ns = new Session(player, level, pos.immutable(), node.id, hand, stack.getItem(), params, now);
        if (!params.simple()) {
            if (!Hooks.kube().pryPhase(player, node, Phase.INSERT.id(), params.rust())) {
                player.displayClientMessage(Component.translatable("manholes.message.pry_cancelled.script"), true);
                MUST_RELEASE.put(player.getUUID(), now);
                return;
            }
        }
        SESSIONS.put(player.getUUID(), ns);
        play(ns, params.simple() ? ModRegistry.SOUND_PRY.get() : ModRegistry.SOUND_INSERT.get(), 1.0f);
        alertMobs(player, level, pos, ManholesConfig.i(ManholesConfig.NOISE_RADIUS));
        if (!params.simple()) {
            sendState(ns);
        }
    }

    private static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        if (!MUST_RELEASE.isEmpty()) {
            long now = server.overworld().getGameTime();
            MUST_RELEASE.values().removeIf(t -> Math.abs(now - t) > 20 * 60); // stale entries
        }
        if (SESSIONS.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        while (it.hasNext()) {
            Session s = it.next().getValue();
            ServerPlayer player = s.player;
            if (player.isRemoved() || player.level() != s.level || !player.isAlive()) {
                it.remove();
                continue;
            }
            long now = s.level.getGameTime();
            String cancel = null;
            if (now - s.lastUse > RELEASE_TIMEOUT) {
                cancel = "released";
            } else if (player.position().distanceTo(s.start) > 1.0) {
                cancel = "moved";
            } else if (player.getInventory().selected != s.slot && s.hand == InteractionHand.MAIN_HAND
                    || !player.getItemInHand(s.hand).is(s.item)) {
                cancel = "switched";
            } else if (!(s.level.getBlockState(s.pos).getBlock() instanceof ManholeBlock)) {
                cancel = "gone";
            }
            if (cancel != null) {
                it.remove();
                player.displayClientMessage(Component.translatable("manholes.message.pry_cancelled." + cancel), true);
                if (!s.params.simple()) {
                    Net.send(player, PryStatePayload.NONE);
                }
                continue;
            }
            s.ticks++;
            if (s.ticks % 20 == 0) {
                alertMobs(player, s.level, s.pos, ManholesConfig.i(ManholesConfig.NOISE_RADIUS));
            }
            if (s.params.simple()) {
                if (tickSimple(s)) {
                    it.remove();
                    finish(player, s);
                }
                continue;
            }
            switch (tickPhased(s, now)) {
                case CONTINUE -> sendState(s);
                case DONE -> {
                    it.remove();
                    Net.send(player, PryStatePayload.NONE);
                    finish(player, s);
                }
                case ABORT -> {
                    it.remove();
                    Net.send(player, PryStatePayload.NONE);
                }
            }
        }
    }

    /** The 1.0 behaviour. Returns true when done. */
    private static boolean tickSimple(Session s) {
        if (s.ticks % 10 == 0) {
            play(s, ModRegistry.SOUND_PRY.get(), 0.85f + s.level.random.nextFloat() * 0.3f);
        }
        int pryTicks = s.params.pryTicks();
        if (s.ticks >= pryTicks) {
            return true;
        }
        if (s.ticks % 2 == 0) {
            s.player.displayClientMessage(progressBar(s.ticks, pryTicks), true);
        }
        return false;
    }

    private enum Step { CONTINUE, DONE, ABORT }

    private static Step tickPhased(Session s, long now) {
        PryParams p = s.params;
        switch (s.phase) {
            case INSERT -> {
                if (s.ticks % 10 == 0) {
                    play(s, ModRegistry.SOUND_PRY.get(), 0.85f + s.level.random.nextFloat() * 0.3f);
                }
                if (++s.phaseTicks >= p.insertTicks()) {
                    return enter(s, Phase.LEVER) ? Step.CONTINUE : Step.ABORT;
                }
            }
            case LEVER -> {
                s.phaseTicks++;
                if (s.pressed) {
                    s.pressed = false;
                    s.idleTicks = 0;
                } else {
                    s.idleTicks++;
                    s.lever = Math.max(0f, s.lever - p.decay());
                }
                if (s.lever >= 1f) {
                    play(s, ModRegistry.SOUND_SKILL_SUCCESS.get(), 1.0f);
                    return enter(s, Phase.SLIDE) ? Step.CONTINUE : Step.ABORT;
                }
                if (s.lever <= 0f && s.peak > 0f && s.peak >= p.giveUp() && p.giveUp() < 1f) {
                    slip(s);
                    return Step.ABORT;
                }
            }
            case SLIDE -> {
                if (s.phaseTicks % 15 == 0) {
                    play(s, ModRegistry.SOUND_SLIDE.get(), 0.9f + s.level.random.nextFloat() * 0.2f);
                }
                if (++s.phaseTicks >= p.slideTicks()) {
                    return Step.DONE;
                }
            }
        }
        return Step.CONTINUE;
    }

    /** Enters a phase; false if a script cancelled it (the attempt is aborted). */
    private static boolean enter(Session s, Phase phase) {
        NodeRecord node = node(s);
        if (node != null && !Hooks.kube().pryPhase(s.player, node, phase.id(), s.params.rust())) {
            s.player.displayClientMessage(Component.translatable("manholes.message.pry_cancelled.script"), true);
            MUST_RELEASE.put(s.player.getUUID(), s.level.getGameTime());
            return false;
        }
        s.phase = phase;
        s.phaseTicks = 0;
        if (phase == Phase.LEVER) {
            play(s, ModRegistry.SOUND_LEVER.get(), 0.8f);
        }
        return true;
    }

    /**
     * One press of the mash key (client packet, or a test). Ignored outside a LEVER phase; at most
     * {@code maxPressesPerSecond} presses count in any 20-tick window. Returns true if the press was accepted.
     */
    public static boolean handleMash(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null || s.params.simple() || s.phase != Phase.LEVER) {
            return false; // stale or unsolicited press
        }
        long now = s.level.getGameTime();
        while (!s.window.isEmpty() && now - s.window.peekFirst() >= RATE_WINDOW) {
            s.window.pollFirst();
        }
        if (s.window.size() >= s.params.maxPressesPerSecond()) {
            s.rejected++;
            return false;
        }
        s.window.addLast(now);
        float amount = s.params.perPress();
        NodeRecord node = node(s);
        if (node != null) {
            amount = Hooks.kube().mash(player, node, s.lever, s.presses + 1, amount);
        }
        s.presses++;
        s.pressed = true;
        s.lever = Math.max(0f, Math.min(1f, s.lever + amount));
        s.peak = Math.max(s.peak, s.lever);
        if (s.presses % Math.max(1, s.params.noiseEvery()) == 0) {
            play(s, ModRegistry.SOUND_LEVER.get(), 0.8f + s.lever * 0.4f);
            alertMobs(player, s.level, s.pos, ManholesConfig.i(ManholesConfig.NOISE_RADIUS));
        }
        return true;
    }

    /** The give-up rule: loud noise, extra tool damage, "The crowbar slips.", the key must be released. */
    private static void slip(Session s) {
        s.level.playSound(null, s.pos, ModRegistry.SOUND_SKILL_FAIL.get(), SoundSource.BLOCKS, 1.6f, 0.9f);
        int r = (int) Math.round(ManholesConfig.i(ManholesConfig.NOISE_RADIUS) * ManholesConfig.d(ManholesConfig.FAIL_NOISE_MULTIPLIER));
        alertMobs(s.player, s.level, s.pos, r);
        int cost = ManholesConfig.i(ManholesConfig.FAIL_DURABILITY_COST);
        ItemStack tool = s.player.getItemInHand(s.hand);
        if (cost > 0 && tool.isDamageableItem()) {
            tool.hurtAndBreak(cost, s.player, p -> p.broadcastBreakEvent(s.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND));
        }
        s.player.displayClientMessage(Component.translatable("manholes.message.pry_slipped").withStyle(ChatFormatting.RED), true);
        MUST_RELEASE.put(s.player.getUUID(), s.level.getGameTime());
    }

    private static void sendState(Session s) {
        PryParams p = s.params;
        float progress = switch (s.phase) {
            case INSERT -> s.phaseTicks / (float) p.insertTicks();
            case LEVER -> s.lever;
            case SLIDE -> s.phaseTicks / (float) p.slideTicks();
        };
        boolean danger = s.phase == Phase.LEVER && s.idleTicks >= 3 && s.lever < DANGER && s.peak > s.lever + 0.05f;
        String look = s.level.getBlockEntity(s.pos) instanceof ManholeBlockEntity be ? be.look() : "";
        Net.send(s.player, new PryStatePayload(s.phase.ordinal() + 1, Math.min(1f, progress), p.rust(), s.presses, danger, look,
                s.hand == InteractionHand.OFF_HAND));
    }

    private static void play(Session s, SoundEvent sound, float pitch) {
        s.level.playSound(null, s.pos, sound, SoundSource.BLOCKS, 1.0f, pitch);
    }

    private static NodeRecord node(Session s) {
        NodeRecord node = ManholeData.get(s.level.getServer()).node(s.nodeId);
        return node != null ? node : ManholesAPI.nodeAt(s.level, s.pos);
    }

    private static Component progressBar(int ticks, int total) {
        int bars = 20;
        int filled = Math.min(bars, ticks * bars / Math.max(1, total));
        return Component.translatable("manholes.message.prying",
                Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GREEN)
                        .append(Component.literal("|".repeat(bars - filled)).withStyle(ChatFormatting.DARK_GRAY)));
    }

    private static void finish(ServerPlayer player, Session s) {
        NodeRecord node = node(s);
        if (node == null) {
            return;
        }
        UUID owner = Owners.ownerOf(player);
        if (!Hooks.kube().pried(player, node, owner)) {
            return; // a script cancelled it (and should say why)
        }
        ManholesAPI.open(player, node);
        s.level.playSound(null, s.pos, ModRegistry.SOUND_OPEN.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(Component.translatable("manholes.message.opened", node.displayName()), true);
        ItemStack tool = player.getItemInHand(s.hand);
        if (tool.isDamageableItem()) {
            tool.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(s.hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND));
        }
        ManholeInteraction.debounce(player, 20); // the use key is probably still held: don't pop the screen at once
    }

    /** The grinding draws the undead: mobs in #manholes:attracted_by_noise target the player / walk to him. */
    private static void alertMobs(ServerPlayer player, ServerLevel level, BlockPos pos, int r) {
        if (r <= 0 || player.isCreative() || player.isSpectator()) {
            return;
        }
        AABB box = new AABB(pos).inflate(r);
        double r2 = (double) r * r;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && m.getType().is(ModRegistry.ATTRACTED_BY_NOISE) && m.distanceToSqr(player) <= r2)) {
            if (mob.getTarget() == null || !mob.getTarget().isAlive()) {
                mob.setTarget(player);
            }
            mob.getNavigation().moveTo(player, 1.0);
        }
    }

    private static void onDamage(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
            Session s = SESSIONS.remove(player.getUUID());
            if (s != null) {
                player.displayClientMessage(Component.translatable("manholes.message.pry_cancelled.hurt"), true);
                if (!s.params.simple()) {
                    Net.send(player, PryStatePayload.NONE);
                }
            }
        }
    }

    // ---------------------------------------------------------------- inspection (game tests, debugging)

    /** Read-only view of a session. {@code phase} is "insert", "lever", "slide" or "simple". */
    public record Snapshot(String phase, float lever, float peak, int presses, int rejected, PryParams params) {}

    public static Snapshot snapshot(ServerPlayer player) {
        Session s = SESSIONS.get(player.getUUID());
        if (s == null) {
            return null;
        }
        return new Snapshot(s.params.simple() ? "simple" : s.phase.id(), s.lever, s.peak, s.presses, s.rejected, s.params);
    }
}
