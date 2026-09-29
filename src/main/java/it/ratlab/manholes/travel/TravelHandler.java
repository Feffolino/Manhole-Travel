// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.compat.Hooks;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.net.FadePayload;
import it.ratlab.manholes.net.OpenTravelScreenPayload;
import it.ratlab.manholes.net.RenameNodePayload;
import it.ratlab.manholes.net.TravelAnimPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Travel screen data, trip validation, costs, the fade, the teleport and ambushes. Server authoritative. */
public final class TravelHandler {
    private static final ResourceLocation FREEZE_ID = Manholes.id("travel_freeze");
    /** Max distance from the manhole the travel screen was opened at. */
    private static final double USE_RANGE = 8.0;

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_UNTIL = new HashMap<>();

    /**
     * One trip. Timeline: [descent (animated only)] [fade / trip] teleport [ascent (animated only)] end. The player is
     * invulnerable and held at {@code anchor} for the whole session.
     */
    private static final class Session {
        final UUID nodeId;
        /** Where the player stood when the trip started (restored if he logs out before the teleport). */
        final Vec3 startPos;
        final boolean scripted;
        final boolean animated;
        Vec3 anchor;
        int teleportTick;
        int endTick;
        boolean arrived;

        Session(UUID nodeId, Vec3 startPos, boolean scripted, boolean animated, Vec3 anchor, int teleportTick, int endTick) {
            this.nodeId = nodeId;
            this.startPos = startPos;
            this.scripted = scripted;
            this.animated = animated;
            this.anchor = anchor;
            this.teleportTick = teleportTick;
            this.endTick = endTick;
        }
    }

    private TravelHandler() {}

    /** Total ticks of a trip with the current config (descent + fade + ascent, or just the fade). */
    public static int tripTicks() {
        int fade = ManholesConfig.i(ManholesConfig.TRAVEL_FADE_TICKS);
        if (!ManholesConfig.b(ManholesConfig.ANIMATION_ENABLED)) {
            return fade;
        }
        return ManholesConfig.i(ManholesConfig.DESCENT_TICKS) + fade + ManholesConfig.i(ManholesConfig.ASCENT_TICKS);
    }

    public static void registerEvents(IEventBus bus) {
        bus.addListener(TravelHandler::onServerTick);
        bus.addListener(TravelHandler::onIncomingDamage);
        bus.addListener(TravelHandler::onAttack);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock.class, TravelHandler::onInteract);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickItem.class, TravelHandler::onInteract);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.LeftClickBlock.class, TravelHandler::onInteract);
        bus.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, TravelHandler::onInteract);
        bus.addListener(TravelHandler::onDamaged);
        bus.addListener(TravelHandler::onLogout);
        bus.addListener(TravelHandler::onLogin);
        // Tick counts restart with the next server (singleplayer world switch): forget everything.
        bus.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            SESSIONS.clear();
            LAST_HURT.clear();
            COOLDOWN_UNTIL.clear();
        });
    }

    public static boolean isTravelling(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    // ---------------------------------------------------------------- screen

    public static void openScreen(ServerPlayer player, NodeRecord current) {
        MinecraftServer server = player.server;
        List<OpenTravelScreenPayload.Entry> entries = new ArrayList<>();
        entries.add(entry(player, current));
        for (NodeRecord r : Access.visible(player)) {
            if (r.id.equals(current.id) || ManholesAPI.pruneIfGone(server, r)) {
                continue;
            }
            entries.add(entry(player, r));
        }
        send(player, new OpenTravelScreenPayload(current.id, entries,
                ManholesConfig.b(ManholesConfig.ALLOW_CROSS_DIMENSION)));
    }

    /** A node as this player sees it: the owner's alias (if any) and the rust level. */
    public static OpenTravelScreenPayload.Entry entry(ServerPlayer player, NodeRecord r) {
        ManholeData data = ManholeData.get(player.server);
        UUID owner = Owners.ownerOf(player);
        boolean aliased = !data.alias(owner, r.id).isEmpty();
        return new OpenTravelScreenPayload.Entry(r.id, data.displayName(owner, r, player.registryAccess()), r.dimension.location(),
                r.pos, rustOf(player.server, r), aliased, r.lookOrDefault(), r.home, r.home ? r.ownerName : "",
                r.home && Access.isOwner(player, r), r.home && Access.isShared(r));
    }

    /** Rust of a node: from its block entity when loaded, else derived from the id like the block entity does. */
    static int rustOf(MinecraftServer server, NodeRecord r) {
        if (r.home || !ManholesConfig.b(ManholesConfig.RUST_ENABLED)) {
            return 0;
        }
        ServerLevel level = server.getLevel(r.dimension);
        if (level != null && level.isLoaded(r.pos) && level.getBlockEntity(r.pos) instanceof ManholeBlockEntity be) {
            return be.rust();
        }
        return ManholeBlockEntity.rustFor(r.id);
    }

    /**
     * Travel-screen rename: a per-owner alias, only for nodes in the player's network. Empty clears it. The new list goes
     * out with the next network sync (end of this tick), which also updates the FTB Chunks icons and an open screen.
     */
    public static void handleRename(ServerPlayer player, UUID nodeId, String name) {
        ManholeData data = ManholeData.get(player.server);
        UUID owner = Owners.ownerOf(player);
        NodeRecord r = data.node(nodeId);
        if (r == null || !Access.canSee(player, r)) {
            fail(player, "not_in_network");
            return;
        }
        String clean = sanitizeAlias(name);
        if (data.setAlias(owner, nodeId, clean)) {
            player.displayClientMessage(clean.isEmpty()
                    ? Component.translatable("manholes.message.alias_cleared", r.displayName(player.registryAccess()))
                    : Component.translatable("manholes.message.renamed", Component.literal(clean)), true);
        }
    }

    /** Strips control / formatting characters and clamps to {@link RenameNodePayload#MAX_LENGTH} code points. */
    public static String sanitizeAlias(String name) {
        if (name == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        name.strip().codePoints().filter(c -> c >= 0x20 && c != 0x7F && c != 0xA7).limit(RenameNodePayload.MAX_LENGTH)
                .forEach(b::appendCodePoint);
        return b.toString().strip();
    }

    /** Only sends to connections that negotiated our channel (fake players / test players don't). */
    private static void send(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        it.ratlab.manholes.net.Net.send(player, payload); // skips fake players (no channel) instead of throwing
    }

    // ---------------------------------------------------------------- requests

    /** A trip chosen on the travel screen. Everything is validated again here. */
    public static void handleRequest(ServerPlayer player, UUID fromId, UUID toId) {
        ManholeData data = ManholeData.get(player.server);
        NodeRecord from = data.node(fromId);
        NodeRecord to = data.node(toId);
        UUID owner = Owners.ownerOf(player);
        if (from == null || to == null || fromId.equals(toId)) {
            fail(player, "node_gone");
            return;
        }
        if (!Access.canSee(player, from) || !Access.canSee(player, to)) {
            fail(player, "not_in_network");
            return;
        }
        if (!from.dimension.equals(player.level().dimension()) || from.pos.getCenter().distanceTo(player.position()) > USE_RANGE) {
            fail(player, "too_far");
            return;
        }
        if (!to.dimension.equals(from.dimension) && !ManholesConfig.b(ManholesConfig.ALLOW_CROSS_DIMENSION)) {
            fail(player, "cross_dimension");
            return;
        }
        if (ManholesAPI.pruneIfGone(player.server, to)) {
            fail(player, "node_gone");
            return;
        }
        String blocker = blocker(player);
        if (blocker != null) {
            fail(player, blocker);
            return;
        }
        // Cost: at least 1 km. Coordinates are scaled by the dimension (nether x8) so cross-dimension trips make sense.
        double km = Math.max(1.0, distance(player.server, from, to) / 1000.0);
        double hunger = ManholesConfig.d(ManholesConfig.HUNGER_COST_PER_KM) * km;
        long time = Math.round(ManholesConfig.i(ManholesConfig.TIME_COST_PER_KM) * km);
        Predicate<ItemStack> costItem = costItem();
        int costCount = ManholesConfig.i(ManholesConfig.EXTRA_ITEM_COST_COUNT);
        if (costItem != null && costCount > 0 && !player.getAbilities().instabuild && count(player.getInventory(), costItem) < costCount) {
            player.displayClientMessage(Component.translatable("manholes.travel.blocked.item_cost", costCount,
                    Component.literal(ManholesConfig.s(ManholesConfig.EXTRA_ITEM_COST))), true);
            return;
        }
        TravelContext ctx = new TravelContext(player, from, to, hunger, time);
        if (!Hooks.kube().travel(ctx)) {
            return;
        }
        // Apply costs.
        if (costItem != null && costCount > 0 && !player.getAbilities().instabuild) {
            consume(player.getInventory(), costItem, costCount);
        }
        if (ctx.hunger > 0 && !player.getAbilities().instabuild) {
            player.causeFoodExhaustion((float) ctx.hunger);
        }
        if (ctx.timeTicks > 0 && player.server.getPlayerCount() == 1) {
            ServerLevel overworld = player.server.overworld();
            overworld.setDayTime(overworld.getDayTime() + ctx.timeTicks);
        }
        start(player, from, to, false);
    }

    /** Scripted / command travel: no membership, blocker or cost checks, same fade. */
    public static boolean startScripted(ServerPlayer player, NodeRecord to) {
        if (isTravelling(player) || ManholesAPI.pruneIfGone(player.server, to)) {
            return false;
        }
        start(player, ManholesAPI.nearest(player.serverLevel(), player.blockPosition(), USE_RANGE), to, true);
        return true;
    }

    @Nullable
    private static String blocker(ServerPlayer player) {
        int now = player.server.getTickCount();
        if (isTravelling(player)) {
            return "travelling";
        }
        Integer hurt = LAST_HURT.get(player.getUUID());
        if (hurt != null && now - hurt < ManholesConfig.i(ManholesConfig.COMBAT_COOLDOWN_TICKS)) {
            return "combat";
        }
        if (player.isPassenger()) {
            return "riding";
        }
        if (player.isSleeping()) {
            return "sleeping";
        }
        Integer until = COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && now < until) {
            player.displayClientMessage(Component.translatable("manholes.travel.blocked.cooldown",
                    (until - now + 19) / 20), true);
            return "";
        }
        return null;
    }

    private static void fail(ServerPlayer player, String key) {
        if (!key.isEmpty()) {
            player.displayClientMessage(Component.translatable("manholes.travel.blocked." + key), true);
        }
    }

    private static double distance(MinecraftServer server, NodeRecord a, NodeRecord b) {
        double sa = scale(server, a);
        double sb = scale(server, b);
        double dx = a.pos.getX() * sa - b.pos.getX() * sb;
        double dz = a.pos.getZ() * sa - b.pos.getZ() * sb;
        double dy = a.dimension.equals(b.dimension) ? a.pos.getY() - b.pos.getY() : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double scale(MinecraftServer server, NodeRecord r) {
        ServerLevel l = server.getLevel(r.dimension);
        return l == null ? 1.0 : l.dimensionType().coordinateScale();
    }

    @Nullable
    private static Predicate<ItemStack> costItem() {
        String s = ManholesConfig.s(ManholesConfig.EXTRA_ITEM_COST).trim();
        if (s.isEmpty()) {
            return null;
        }
        if (s.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(s.substring(1));
            if (tag == null) {
                return null;
            }
            TagKey<Item> key = TagKey.create(Registries.ITEM, tag);
            return st -> st.is(key);
        }
        ResourceLocation id = ResourceLocation.tryParse(s);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        return st -> st.is(item);
    }

    private static int count(Inventory inv, Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && p.test(st)) {
                n += st.getCount();
            }
        }
        return n;
    }

    private static void consume(Inventory inv, Predicate<ItemStack> p, int amount) {
        for (int i = 0; i < inv.getContainerSize() && amount > 0; i++) {
            ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && p.test(st)) {
                int take = Math.min(amount, st.getCount());
                st.shrink(take);
                amount -= take;
            }
        }
        inv.setChanged();
    }

    // ---------------------------------------------------------------- fade session

    private static void start(ServerPlayer player, @Nullable NodeRecord from, NodeRecord to, boolean scripted) {
        int fade = ManholesConfig.i(ManholesConfig.TRAVEL_FADE_TICKS);
        player.closeContainer();
        player.stopRiding();
        int now = player.server.getTickCount();
        Vec3 startPos = player.position();
        boolean animated = ManholesConfig.b(ManholesConfig.ANIMATION_ENABLED);
        if (!animated) {
            SESSIONS.put(player.getUUID(), new Session(to.id, startPos, scripted, false, startPos, now + fade, now + fade));
            freeze(player, true);
            send(player, new FadePayload(fade));
            return;
        }
        int descent = ManholesConfig.i(ManholesConfig.DESCENT_TICKS);
        // Step onto the centre of the cover we're going down (server-validated final position); without a start
        // cover (scripted trip far from any manhole) the player climbs down where he stands.
        Vec3 cover = startPos;
        float yaw = player.getYRot();
        if (from != null && from.dimension.equals(player.level().dimension())
                && player.serverLevel().getBlockState(from.pos).getBlock() instanceof ManholeBlock) {
            BlockState st = player.serverLevel().getBlockState(from.pos);
            cover = coverTop(player.serverLevel(), from.pos);
            yaw = st.getValue(ManholeBlock.FACING).toYRot();
        }
        send(player, new TravelAnimPayload(TravelAnimPayload.DESCENT, startPos, player.getYRot(), player.getXRot(), cover, yaw,
                cover, descent, fade));
        player.connection.teleport(cover.x, cover.y, cover.z, yaw, player.getXRot());
        SESSIONS.put(player.getUUID(), new Session(to.id, startPos, scripted, true, cover, now + descent + fade,
                now + descent + fade + ManholesConfig.i(ManholesConfig.ASCENT_TICKS)));
        freeze(player, true);
    }

    /** Feet position on top of a cover (its collision top, 2/16). */
    private static Vec3 coverTop(ServerLevel level, BlockPos pos) {
        var shape = level.getBlockState(pos).getCollisionShape(level, pos);
        double top = shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
        return new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
    }

    private static void freeze(ServerPlayer player, boolean on) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = player.getAttribute(Attributes.JUMP_STRENGTH);
        for (AttributeInstance a : new AttributeInstance[] {speed, jump}) {
            if (a == null) {
                continue;
            }
            a.removeModifier(FREEZE_ID);
            if (on) {
                a.addTransientModifier(new AttributeModifier(FREEZE_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Session>> it = SESSIONS.entrySet().iterator();
        List<Runnable> actions = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Session> e = it.next();
            Session s = e.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
            if (player == null || !player.isAlive()) {
                if (player != null) {
                    freeze(player, false);
                }
                it.remove();
                continue;
            }
            if (!s.arrived && now >= s.teleportTick) {
                s.arrived = true;
                actions.add(() -> arrive(player, s));
            } else if (s.arrived && now >= s.endTick) {
                it.remove();
                actions.add(() -> finish(player, s));
            } else if (player.position().distanceToSqr(s.anchor) > 0.25) {
                // Immobile: undo any push / knockback.
                player.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, player.getYRot(), player.getXRot());
            }
        }
        actions.forEach(Runnable::run); // teleports change dimensions: never inside the iteration
    }

    /** The teleport (the middle of the trip). Arrival effects come in {@link #finish} after the ascent. */
    private static void arrive(ServerPlayer player, Session s) {
        MinecraftServer server = player.server;
        NodeRecord to = ManholeData.get(server).node(s.nodeId);
        ServerLevel level = to == null ? null : server.getLevel(to.dimension);
        if (to == null || level == null) {
            abort(player, s);
            return;
        }
        level.getChunk(to.pos); // load (or generate) the destination chunk
        BlockState cover = level.getBlockState(to.pos);
        if (!(cover.getBlock() instanceof ManholeBlock) || !(level.getBlockEntity(to.pos) instanceof ManholeBlockEntity be)
                || !to.id.equals(be.nodeId())) {
            ManholeData.get(server).removeNode(to.id);
            abort(player, s);
            return;
        }
        Vec3 spot = null;
        float yaw = cover.getValue(ManholeBlock.FACING).toYRot();
        List<Direction> dirs = new ArrayList<>();
        Direction facing = cover.getValue(ManholeBlock.FACING);
        dirs.add(facing);
        dirs.add(facing.getClockWise());
        dirs.add(facing.getCounterClockWise());
        dirs.add(facing.getOpposite());
        outer:
        for (Direction d : dirs) {
            for (int dy : new int[] {0, 1, -1}) {
                BlockPos feet = to.pos.relative(d).above(dy);
                // 1.7.0: wide covers overhang 4 px into this cell; land 0.1 further away so the player's box clears it.
                double sx = d.getStepX() * LANDING_SHIFT;
                double sz = d.getStepZ() * LANDING_SHIFT;
                if (standable(level, feet, sx, sz)) {
                    spot = new Vec3(feet.getX() + 0.5 + sx, feet.getY() + floorOffset(level, feet), feet.getZ() + 0.5 + sz);
                    yaw = d.toYRot(); // facing away from the cover
                    break outer;
                }
            }
        }
        if (spot == null) {
            spot = coverTop(level, to.pos); // on the cover itself
        }
        player.teleportTo(level, spot.x, spot.y, spot.z, yaw, 0.0f);
        player.fallDistance = 0;
        s.anchor = spot;
        COOLDOWN_UNTIL.put(player.getUUID(), server.getTickCount() + ManholesConfig.i(ManholesConfig.TRAVEL_COOLDOWN_TICKS));
        level.playSound(null, to.pos, ModRegistry.SOUND_ARRIVE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        if (s.animated) {
            int ascent = Math.max(0, s.endTick - server.getTickCount());
            send(player, new TravelAnimPayload(TravelAnimPayload.ASCENT, spot, yaw, 0f, coverTop(level, to.pos), yaw, spot, ascent, 0));
        } else {
            SESSIONS.remove(player.getUUID());
            finish(player, s);
        }
    }

    /** End of the trip: control comes back, then the arrival message, the arrived event and the ambush roll. */
    private static void finish(ServerPlayer player, Session s) {
        freeze(player, false);
        NodeRecord to = ManholeData.get(player.server).node(s.nodeId);
        if (to == null) {
            return;
        }
        player.displayClientMessage(Component.translatable("manholes.travel.arrived",
                ManholeData.get(player.server).displayName(Owners.ownerOf(player), to, player.registryAccess())), true);
        boolean builtinAmbush = Hooks.kube().arrived(player, to);
        if (builtinAmbush && !s.scripted && player.level() instanceof ServerLevel level) {
            ambush(player, level, to.pos);
        }
    }

    /** The destination is gone: back to where the trip started. */
    private static void abort(ServerPlayer player, Session s) {
        SESSIONS.remove(player.getUUID());
        freeze(player, false);
        player.connection.teleport(s.startPos.x, s.startPos.y, s.startPos.z, player.getYRot(), player.getXRot());
        send(player, new FadePayload(0));
        player.displayClientMessage(Component.translatable("manholes.travel.blocked.node_gone"), true);
    }

    /**
     * Feet position with room for a player: something solid under the feet (or a low block at the feet, like a slab), two
     * full blocks of free space above the standing surface (so with a slab at the feet, the blocks at +1 and +2 must be
     * empty too), no collision for the player's box there, no hazards.
     */
    public static boolean standable(ServerLevel level, BlockPos feet) {
        return standable(level, feet, 0, 0);
    }

    /** Landing spot shift away from the cover (clears the 4 px overhang of wide covers with a 0.3 half-width box). */
    static final double LANDING_SHIFT = 0.1;

    /** {@link #standable(ServerLevel, BlockPos)} with the player's box shifted by (sx, sz) from the cell centre. */
    public static boolean standable(ServerLevel level, BlockPos feet, double sx, double sz) {
        BlockState f = level.getBlockState(feet);
        BlockState h = level.getBlockState(feet.above());
        BlockState g = level.getBlockState(feet.below());
        double off = floorOffset(level, feet);
        if (!f.getCollisionShape(level, feet).isEmpty() && off == 0) {
            return false;
        }
        if (!h.getCollisionShape(level, feet.above()).isEmpty()) {
            return false;
        }
        if (off > 0) {
            BlockPos top = feet.above(2);
            BlockState t = level.getBlockState(top);
            if (!t.getCollisionShape(level, top).isEmpty() || !safe(level, top, t)) {
                return false;
            }
        }
        double y = feet.getY() + off;
        net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(feet.getX() + 0.2 + sx, y + 0.01, feet.getZ() + 0.2 + sz,
                feet.getX() + 0.8 + sx, y + 1.8, feet.getZ() + 0.8 + sz);
        if (!level.noCollision(box)) {
            return false;
        }
        boolean floor = !g.getCollisionShape(level, feet.below()).isEmpty() || off > 0;
        return floor && safe(level, feet, f) && safe(level, feet.above(), h) && !g.is(BlockTags.FIRE)
                && !level.getFluidState(feet.below()).is(FluidTags.LAVA) && !g.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK);
    }

    private static boolean safe(ServerLevel level, BlockPos pos, BlockState s) {
        return level.getFluidState(pos).isEmpty() && !s.is(BlockTags.FIRE) && !s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !s.is(net.minecraft.world.level.block.Blocks.CACTUS) && !s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH);
    }

    /** Height of a low block (slab, carpet, snow layer, another cover) at feet that the player stands on; 0 if none. */
    private static double floorOffset(ServerLevel level, BlockPos feet) {
        var shape = level.getBlockState(feet).getCollisionShape(level, feet);
        if (shape.isEmpty()) {
            return 0;
        }
        double top = shape.max(Direction.Axis.Y);
        return top <= 0.5 ? top : 0;
    }

    // ---------------------------------------------------------------- ambush

    private static void ambush(ServerPlayer player, ServerLevel level, BlockPos at) {
        double chance = ManholesConfig.d(ManholesConfig.AMBUSH_CHANCE);
        if (chance <= 0 || level.random.nextDouble() >= chance || player.isCreative() || player.isSpectator()) {
            return;
        }
        Optional<HolderSet.Named<EntityType<?>>> tag = BuiltInRegistries.ENTITY_TYPE.getTag(ModRegistry.AMBUSH_MOBS);
        if (tag.isEmpty() || tag.get().size() == 0) {
            return;
        }
        int min = ManholesConfig.i(ManholesConfig.AMBUSH_COUNT_MIN);
        int max = Math.max(min, ManholesConfig.i(ManholesConfig.AMBUSH_COUNT_MAX));
        int count = Mth.nextInt(level.random, min, max);
        for (int i = 0; i < count; i++) {
            Holder<EntityType<?>> type = tag.get().getRandomElement(level.random).orElse(null);
            if (type == null) {
                return;
            }
            BlockPos pos = ambushSpot(player, level, at);
            if (pos == null) {
                continue;
            }
            Entity e = type.value().spawn(level, pos, MobSpawnType.EVENT);
            if (e instanceof Mob mob) {
                mob.setTarget(player);
            }
        }
    }

    @Nullable
    private static BlockPos ambushSpot(ServerPlayer player, ServerLevel level, BlockPos at) {
        BlockPos visible = null;
        Vec3 eye = player.getEyePosition();
        for (int attempt = 0; attempt < 24; attempt++) {
            double ang = level.random.nextDouble() * Math.PI * 2;
            double dist = 6 + level.random.nextDouble() * 8;
            int x = Mth.floor(at.getX() + 0.5 + Math.cos(ang) * dist);
            int z = Mth.floor(at.getZ() + 0.5 + Math.sin(ang) * dist);
            if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) {
                continue;
            }
            // Same floor as the destination first (streets, tunnels), then the surface.
            BlockPos pos = null;
            for (int dy = 2; dy >= -2 && pos == null; dy--) {
                BlockPos p = new BlockPos(x, at.getY() + dy, z);
                if (standable(level, p) && floorOffset(level, p) == 0) {
                    pos = p;
                }
            }
            if (pos == null) {
                BlockPos p = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
                if (Math.abs(p.getY() - at.getY()) <= 6 && standable(level, p)) {
                    pos = p;
                }
            }
            if (pos == null) {
                continue;
            }
            BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos.above()), ClipContext.Block.VISUAL,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                return pos; // out of sight
            }
            if (visible == null) {
                visible = pos;
            }
        }
        return visible;
    }

    // ---------------------------------------------------------------- events

    /** No fighting or using things while invulnerable on the way. */
    private static void onAttack(net.neoforged.neoforge.event.entity.player.AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())
                && event instanceof net.neoforged.bus.api.ICancellableEvent c) {
            c.setCanceled(true);
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())) {
            event.setCanceled(true); // invulnerable during the fade
        }
    }

    private static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getNewDamage() > 0) {
            LAST_HURT.put(p.getUUID(), p.server.getTickCount());
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        Session s = SESSIONS.remove(id);
        if (s != null && event.getEntity() instanceof ServerPlayer p) {
            // Fired before the player is saved: never leave him mid-climb on the cover or half-way somewhere.
            freeze(p, false);
            Vec3 safe = s.arrived ? s.anchor : s.startPos;
            p.setPos(safe.x, safe.y, safe.z);
        }
        LAST_HURT.remove(id);
        ManholeInteraction.forget(id);
    }

    /** Players who joined a team later get the stages of the nodes their team already opened. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            for (NodeRecord r : ManholeData.get(p.server).network(Owners.ownerOf(p))) {
                Owners.grantStage(p, r);
            }
        }
    }
}
