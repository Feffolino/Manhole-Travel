// SPDX-License-Identifier: MIT
package it.ratlab.manholes.data;

import com.google.gson.JsonElement;
import java.util.Locale;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Node names are stored as strings: plain text, or a JSON text component when they start with { [ or ". */
public final class Names {
    private Names() {}

    public static Component parse(String raw, HolderLookup.Provider registries) {
        String s = raw.trim();
        if (s.startsWith("{") || s.startsWith("[") || s.startsWith("\"")) {
            try {
                Component c = Component.Serializer.fromJson(s, registries);
                if (c != null) {
                    return c;
                }
            } catch (Exception ignored) {
                // not valid JSON: show it verbatim
            }
        }
        return Component.literal(raw);
    }

    public static String toRaw(Component c, HolderLookup.Provider registries) {
        return Component.Serializer.toJson(c, registries);
    }

    public static String toRaw(JsonElement json) {
        return json.isJsonPrimitive() ? json.getAsString() : json.toString();
    }

    /** "minecraft:pillager_outpost" -> "Pillager Outpost". */
    public static String prettify(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) {
            path = path.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String word : path.split("[_\\-]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return sb.toString();
    }

    /** Name taken from a structure id: lang key structure.<ns>.<path> with a prettified fallback. */
    public static Component fromStructure(ResourceLocation id) {
        return Component.translatableWithFallback("structure." + id.getNamespace() + "." + id.getPath().replace('/', '.'), prettify(id));
    }
}
