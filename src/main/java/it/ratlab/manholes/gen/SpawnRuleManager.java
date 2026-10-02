// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.compat.Hooks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;

/**
 * Loads spawn rules on server start and /reload:
 * <ul>
 *   <li>{@code data/<ns>/manholes/spawn_rule/*.json} (datapacks, kubejs/data) - always active;</li>
 *   <li>{@code data/<ns>/manholes/builtin_spawn_rule/*.json} (shipped in the jar) - only with naturalSpawn = true;</li>
 *   <li>then ManholeEvents.spawnRules (KubeJS) may add / remove / modify rules.</li>
 * </ul>
 */
public final class SpawnRuleManager extends SimplePreparableReloadListener<SpawnRuleManager.Raw> {
    public static final String DIR = "manholes/spawn_rule";
    public static final String BUILTIN_DIR = "manholes/builtin_spawn_rule";
    private static final Gson GSON = new GsonBuilder().setLenient().create();

    private static volatile List<SpawnRule> structureRules = List.of();
    private static volatile List<SpawnRule> scatterRules = List.of();
    private static volatile List<ResourceLocation> allIds = List.of();

    record Raw(Map<ResourceLocation, JsonElement> rules, Map<ResourceLocation, JsonElement> builtin) {}

    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new SpawnRuleManager());
    }

    @Override
    protected Raw prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonElement> rules = new TreeMap<>();
        Map<ResourceLocation, JsonElement> builtin = new TreeMap<>();
        SimpleJsonResourceReloadListener.scanDirectory(manager, DIR, GSON, rules);
        SimpleJsonResourceReloadListener.scanDirectory(manager, BUILTIN_DIR, GSON, builtin);
        return new Raw(rules, builtin);
    }

    @Override
    protected void apply(Raw raw, ResourceManager manager, ProfilerFiller profiler) {
        SpawnRuleSet set = new SpawnRuleSet();
        boolean natural = ManholesConfig.b(ManholesConfig.NATURAL_SPAWN);
        int builtinCount = 0;
        if (natural) {
            for (Map.Entry<ResourceLocation, JsonElement> e : raw.builtin.entrySet()) {
                SpawnRule r = parse(e.getKey(), e.getValue());
                if (r != null) {
                    r.builtin = true;
                    set.put(r);
                    builtinCount++;
                }
            }
        }
        int dataCount = 0;
        for (Map.Entry<ResourceLocation, JsonElement> e : raw.rules.entrySet()) {
            SpawnRule r = parse(e.getKey(), e.getValue());
            if (r != null) {
                set.put(r); // a datapack rule with the same id replaces a built-in one
                dataCount++;
            }
        }
        try {
            Hooks.kube().spawnRules(set);
        } catch (Throwable t) {
            Manholes.LOGGER.error("ManholeEvents.spawnRules failed", t);
        }
        freeze(set);
        Manholes.LOGGER.info("Loaded {} manhole spawn rules after scripts ({} from datapacks, {} built-in{}){}",
                set.all().size(), dataCount, builtinCount, natural ? "" : " - built-ins skipped: naturalSpawn=false",
                ManholesConfig.b(ManholesConfig.DISABLE_ALL_GENERATION) ? " - generation is DISABLED (disableAllGeneration=true)" : "");
        for (SpawnRule r : set.all()) {
            Manholes.LOGGER.info("  spawn rule {}", r);
        }
    }

    private static SpawnRule parse(ResourceLocation id, JsonElement json) {
        try {
            return SpawnRule.fromJson(id, json.getAsJsonObject());
        } catch (Exception ex) {
            Manholes.LOGGER.error("Invalid manhole spawn rule {}: {}", id, ex.getMessage());
            return null;
        }
    }

    private static void freeze(SpawnRuleSet set) {
        List<SpawnRule> st = new ArrayList<>();
        List<SpawnRule> sc = new ArrayList<>();
        for (SpawnRule r : set.all()) {
            (r.type == SpawnRule.Type.STRUCTURE ? st : sc).add(r);
        }
        structureRules = List.copyOf(st);
        scatterRules = List.copyOf(sc);
        allIds = List.copyOf(set.ids());
    }

    public static List<SpawnRule> structureRules() {
        return structureRules;
    }

    public static List<SpawnRule> scatterRules() {
        return scatterRules;
    }

    public static List<ResourceLocation> ids() {
        return allIds;
    }

    public static Map<ResourceLocation, SpawnRule> byId() {
        Map<ResourceLocation, SpawnRule> m = new HashMap<>();
        structureRules.forEach(r -> m.put(r.id, r));
        scatterRules.forEach(r -> m.put(r.id, r));
        return m;
    }
}
