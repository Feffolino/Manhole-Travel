// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import com.google.gson.JsonObject;
import dev.latvian.mods.kubejs.event.EventJS;
import dev.latvian.mods.kubejs.level.LevelEventJS;
import dev.latvian.mods.kubejs.player.PlayerEventJS;
import it.ratlab.manholes.api.NodeView;
import it.ratlab.manholes.gen.GenerateContext;
import it.ratlab.manholes.gen.SpawnRule;
import it.ratlab.manholes.gen.SpawnRuleSet;
import it.ratlab.manholes.travel.TravelContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Event objects of the {@code ManholeEvents} group. */
public final class ManholeKubeEvents {
    private ManholeKubeEvents() {}

    /** Ids without a namespace get {@code kubejs:}. */
    static ResourceLocation rl(String id) {
        return ResourceLocation.tryParse(id.contains(":") ? id : "kubejs:" + id);
    }

    /** ManholeEvents.spawnRules: add / builder / remove / modify / getIds. */
    public static final class SpawnRules extends EventJS {
        private final SpawnRuleSet set;

        SpawnRules(SpawnRuleSet set) {
            this.set = set;
        }

        /** Adds (or replaces) a rule from a JSON object with the datapack schema. */
        public SpawnRule add(String id, Object json) {
            JsonObject o = JsonConvert.toJsonObject(json);
            return set.add(rl(id), o);
        }

        /** New structure rule builder: {@code event.structure('x').structures('#minecraft:village').chance(0.3)}. */
        public SpawnRule structure(String id) {
            return set.structure(rl(id));
        }

        /** New scatter rule builder: {@code event.scatter('x').onBlocks('#c:...').chancePerChunk(0.01)}. */
        public SpawnRule scatter(String id) {
            return set.scatter(rl(id));
        }

        public boolean remove(String id) {
            return set.remove(rl(id));
        }

        public boolean modify(String id, Consumer<SpawnRule> action) {
            return set.modify(rl(id), action);
        }

        public List<String> getIds() {
            List<String> out = new ArrayList<>();
            set.ids().forEach(r -> out.add(r.toString()));
            return out;
        }
    }

    /** ManholeEvents.generate: a spot chosen by a rule, before placing. Cancel to skip it. */
    public static final class Generate extends LevelEventJS {
        private final GenerateContext ctx;

        Generate(GenerateContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public Level getLevel() {
            return ctx.level;
        }

        public BlockPos getPos() {
            return ctx.pos;
        }

        public void setPos(BlockPos pos) {
            ctx.pos = pos.immutable();
        }

        public String getRuleId() {
            return ctx.ruleId;
        }

        @Nullable
        public String getStructureId() {
            return ctx.structureId;
        }

        /** Raw name: plain text or a JSON text component string; null = default naming. */
        @Nullable
        public String getName() {
            return ctx.name;
        }

        public void setName(@Nullable String name) {
            ctx.name = name == null || name.isEmpty() ? null : name;
        }
    }

    /** ManholeEvents.pried: player, node, team. Cancel to keep it closed. */
    public static final class Pried extends PlayerEventJS {
        private final ServerPlayer player;
        private final NodeView node;
        private final String team;
        private final Component teamName;

        Pried(ServerPlayer player, NodeView node, String team, Component teamName) {
            this.player = player;
            this.node = node;
            this.team = team;
            this.teamName = teamName;
        }

        @Override
        public ServerPlayer getEntity() {
            return player;
        }

        public NodeView getNode() {
            return node;
        }

        /** Network owner id: the FTB team id, or the player uuid without FTB Teams. */
        public String getTeam() {
            return team;
        }

        public String getTeamName() {
            return teamName.getString();
        }
    }

    /** ManholeEvents.travel: player, from, to, editable costs. Cancel to stop the trip. */
    public static final class Travel extends PlayerEventJS {
        private final TravelContext ctx;
        @Nullable
        private final NodeView from;
        private final NodeView to;

        Travel(TravelContext ctx, @Nullable NodeView from, NodeView to) {
            this.ctx = ctx;
            this.from = from;
            this.to = to;
        }

        @Override
        public Player getEntity() {
            return ctx.player;
        }

        @Nullable
        public NodeView getFrom() {
            return from;
        }

        public NodeView getTo() {
            return to;
        }

        public double getHunger() {
            return ctx.hunger;
        }

        public void setHunger(double hunger) {
            ctx.hunger = Math.max(0, hunger);
        }

        public long getTimeTicks() {
            return ctx.timeTicks;
        }

        public void setTimeTicks(long ticks) {
            ctx.timeTicks = Math.max(0, ticks);
        }
    }

    /** ManholeEvents.arrived: player, node. Cancel to skip the built-in ambush roll. */
    public static final class Arrived extends PlayerEventJS {
        private final ServerPlayer player;
        private final NodeView node;

        Arrived(ServerPlayer player, NodeView node) {
            this.player = player;
            this.node = node;
        }

        @Override
        public ServerPlayer getEntity() {
            return player;
        }

        public NodeView getNode() {
            return node;
        }
    }

    /** ManholeEvents.pryPhase: player, node, phase ('insert', 'lever', 'slide'), rust. Cancel to abort the attempt. */
    public static final class PryPhase extends PlayerEventJS {
        private final ServerPlayer player;
        private final NodeView node;
        private final String phase;
        private final int rust;

        PryPhase(ServerPlayer player, NodeView node, String phase, int rust) {
            this.player = player;
            this.node = node;
            this.phase = phase;
            this.rust = rust;
        }

        @Override
        public ServerPlayer getEntity() {
            return player;
        }

        public NodeView getNode() {
            return node;
        }

        public String getPhase() {
            return phase;
        }

        /** Rust level 0..3 used for this attempt (0 with rustEnabled = false). */
        public int getRust() {
            return rust;
        }
    }

    /**
     * ManholeEvents.mash: an accepted press of the mash key during LEVER. {@code progress} (0..100, before this press),
     * {@code presses} (this press included) and the settable {@code amount} (percent of the bar this press adds; 0 =
     * the press does nothing, negative pushes the bar back).
     */
    public static final class Mash extends PlayerEventJS {
        private final ServerPlayer player;
        private final NodeView node;
        private final double progress;
        private final int presses;
        private double amount;

        Mash(ServerPlayer player, NodeView node, float progress, int presses, float amount) {
            this.player = player;
            this.node = node;
            this.progress = progress * 100.0;
            this.presses = presses;
            this.amount = amount * 100.0;
        }

        @Override
        public ServerPlayer getEntity() {
            return player;
        }

        public NodeView getNode() {
            return node;
        }

        /** Bar before this press, percent. */
        public double getProgress() {
            return progress;
        }

        public int getPresses() {
            return presses;
        }

        /** Percent of the bar this press adds. */
        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }

        float amountFraction() {
            return (float) (amount / 100.0);
        }
    }

    /**
     * Deprecated since 1.2.0: skill checks are gone, this event never fires. Kept so scripts written for 1.1 still
     * load (a warning is logged when a script registers it).
     */
    @Deprecated
    public static final class SkillCheck extends PlayerEventJS {
        private final ServerPlayer player;
        private final NodeView node;
        private boolean success;
        private final int fails;

        SkillCheck(ServerPlayer player, NodeView node, boolean success, int fails) {
            this.player = player;
            this.node = node;
            this.success = success;
            this.fails = fails;
        }

        @Override
        public ServerPlayer getEntity() {
            return player;
        }

        public NodeView getNode() {
            return node;
        }

        public boolean getSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getFails() {
            return fails;
        }
    }
}
