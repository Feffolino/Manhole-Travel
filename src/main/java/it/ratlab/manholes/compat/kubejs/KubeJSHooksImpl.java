// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.kubejs;

import dev.latvian.mods.kubejs.core.PlayerKJS;
import dev.latvian.mods.kubejs.event.EventResult;
import dev.latvian.mods.kubejs.stages.Stages;
import it.ratlab.manholes.api.NodeView;
import it.ratlab.manholes.compat.KubeHooks;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.gen.GenerateContext;
import it.ratlab.manholes.gen.SpawnRuleSet;
import it.ratlab.manholes.travel.Owners;
import it.ratlab.manholes.travel.TravelContext;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Posts ManholeEvents and uses KubeJS stages. Only instantiated when KubeJS is loaded. */
public final class KubeJSHooksImpl implements KubeHooks {
    @Override
    public void spawnRules(SpawnRuleSet rules) {
        warnDeprecated();
        if (ManholeEventsJS.SPAWN_RULES.hasListeners()) {
            ManholeEventsJS.SPAWN_RULES.post(new ManholeKubeEvents.SpawnRules(rules));
        }
    }

    @Override
    public boolean generate(GenerateContext ctx) {
        if (!ManholeEventsJS.GENERATE.hasListeners()) {
            return true;
        }
        return !cancelled(ManholeEventsJS.GENERATE.post(new ManholeKubeEvents.Generate(ctx)));
    }

    @Override
    public boolean pried(ServerPlayer player, NodeRecord node, UUID owner) {
        if (!ManholeEventsJS.PRIED.hasListeners()) {
            return true;
        }
        var e = new ManholeKubeEvents.Pried(player, new NodeView(node), owner.toString(),
                Owners.ownerName(player.server, owner, player));
        return !cancelled(ManholeEventsJS.PRIED.post(e));
    }

    @Override
    public boolean pryPhase(ServerPlayer player, NodeRecord node, String phase, int rust) {
        if (!ManholeEventsJS.PRY_PHASE.hasListeners()) {
            return true;
        }
        var e = new ManholeKubeEvents.PryPhase(player, new NodeView(node), phase, rust);
        return !cancelled(ManholeEventsJS.PRY_PHASE.post(e));
    }

    @Override
    public float mash(ServerPlayer player, NodeRecord node, float progress, int presses, float amount) {
        if (!ManholeEventsJS.MASH.hasListeners()) {
            return amount;
        }
        var e = new ManholeKubeEvents.Mash(player, new NodeView(node), progress, presses, amount);
        ManholeEventsJS.MASH.post(e);
        return e.amountFraction();
    }

    @Override
    public boolean travel(TravelContext ctx) {
        if (!ManholeEventsJS.TRAVEL.hasListeners()) {
            return true;
        }
        var e = new ManholeKubeEvents.Travel(ctx, NodeView.of(ctx.from), new NodeView(ctx.to));
        return !cancelled(ManholeEventsJS.TRAVEL.post(e));
    }

    @Override
    public boolean arrived(ServerPlayer player, NodeRecord node) {
        if (!ManholeEventsJS.ARRIVED.hasListeners()) {
            return true;
        }
        return !cancelled(ManholeEventsJS.ARRIVED.post(new ManholeKubeEvents.Arrived(player, new NodeView(node))));
    }

    private static boolean warnedSkillCheck;

    /**
     * ManholeEvents.skillCheck is still registered (so 1.1 scripts load) but never fires since 1.2.0: say so once.
     * spawnRules runs after every server script load (server start and /reload), so this is checked there.
     */
    private static void warnDeprecated() {
        if (ManholeEventsJS.SKILL_CHECK.hasListeners() && !warnedSkillCheck) {
            warnedSkillCheck = true;
            it.ratlab.manholes.Manholes.LOGGER.warn("ManholeEvents.skillCheck is deprecated and never fires since Manhole Travel "
                    + "1.2.0 (skill checks were replaced by button mashing). Use ManholeEvents.mash instead.");
            dev.latvian.mods.kubejs.script.ScriptType.SERVER.console.warn("ManholeEvents.skillCheck is deprecated and never fires "
                    + "(Manhole Travel 1.2.0 replaced skill checks with mashing); use ManholeEvents.mash");
        }
    }

    private static boolean cancelled(EventResult r) {
        return r.interruptFalse();
    }

    private static Stages stages(ServerPlayer player) {
        return ((PlayerKJS) player).kjs$getStages();
    }

    @Override
    public boolean addStage(ServerPlayer player, String stage) {
        stages(player).add(stage);
        return true;
    }

    @Override
    public boolean removeStage(ServerPlayer player, String stage) {
        stages(player).remove(stage);
        return true;
    }
}
