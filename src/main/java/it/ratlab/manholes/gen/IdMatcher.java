// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jetbrains.annotations.Nullable;

/**
 * A list of registry ids, {@code #tags} or {@code "*"}. JSON: a string or an array of strings.
 * An empty matcher means "not set" and matches everything.
 */
public final class IdMatcher {
    public static final IdMatcher ANY = new IdMatcher(List.of());

    private final List<String> entries;

    public IdMatcher(List<String> entries) {
        this.entries = List.copyOf(entries);
    }

    public static IdMatcher of(String... entries) {
        List<String> l = new ArrayList<>();
        for (String e : entries) {
            if (e != null && !e.isBlank()) {
                l.add(e.trim());
            }
        }
        return new IdMatcher(l);
    }

    public static IdMatcher parse(@Nullable JsonElement json) {
        if (json == null || json.isJsonNull()) {
            return ANY;
        }
        List<String> l = new ArrayList<>();
        if (json.isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray()) {
                l.add(e.getAsString().trim());
            }
        } else {
            l.add(json.getAsString().trim());
        }
        return new IdMatcher(l);
    }

    public boolean isAny() {
        return entries.isEmpty() || entries.contains("*");
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<String> entries() {
        return entries;
    }

    public <T> boolean matches(Holder<T> holder, Registry<T> registry) {
        if (isAny()) {
            return true;
        }
        for (String e : entries) {
            if (e.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(e.substring(1));
                if (tag != null && holder.is(TagKey.create(registry.key(), tag))) {
                    return true;
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(e);
                if (id != null && holder.is(ResourceKey.create(registry.key(), id))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Like {@link #matches} but an unset matcher matches nothing. */
    public <T> boolean matchesExplicit(Holder<T> holder, Registry<T> registry) {
        return !entries.isEmpty() && matches(holder, registry);
    }

    public JsonElement toJson() {
        if (entries.size() == 1) {
            return new JsonPrimitive(entries.get(0));
        }
        JsonArray a = new JsonArray();
        entries.forEach(a::add);
        return a;
    }

    @Override
    public String toString() {
        return entries.isEmpty() ? "*" : String.join(",", entries);
    }
}
