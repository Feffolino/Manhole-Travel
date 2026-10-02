// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** The mutable rule set handed to ManholeEvents.spawnRules before it's frozen. */
public final class SpawnRuleSet {
    private final Map<ResourceLocation, SpawnRule> rules = new LinkedHashMap<>();

    public void put(SpawnRule rule) {
        rules.put(rule.id, rule);
    }

    public SpawnRule add(ResourceLocation id, JsonObject json) {
        SpawnRule r = SpawnRule.fromJson(id, json);
        put(r);
        return r;
    }

    public SpawnRule structure(ResourceLocation id) {
        SpawnRule r = new SpawnRule(id, SpawnRule.Type.STRUCTURE);
        put(r);
        return r;
    }

    public SpawnRule scatter(ResourceLocation id) {
        SpawnRule r = new SpawnRule(id, SpawnRule.Type.SCATTER);
        put(r);
        return r;
    }

    public boolean remove(ResourceLocation id) {
        return rules.remove(id) != null;
    }

    public boolean modify(ResourceLocation id, Consumer<SpawnRule> action) {
        SpawnRule r = rules.get(id);
        if (r == null) {
            return false;
        }
        action.accept(r);
        return true;
    }

    @Nullable
    public SpawnRule get(ResourceLocation id) {
        return rules.get(id);
    }

    public List<ResourceLocation> ids() {
        return new ArrayList<>(rules.keySet());
    }

    public Collection<SpawnRule> all() {
        return rules.values();
    }
}
