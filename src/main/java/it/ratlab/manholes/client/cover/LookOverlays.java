// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.ratlab.manholes.Manholes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6.0: parser of a look's optional {@code condition_overlays}
 * ({@code [{"part": i, "levels": {"1": model, "2": model, "3": model}}]}). Uses no client classes, so the game tests
 * can call it on the dedicated server; {@link CoverLooks} turns the model ids into standalone model locations.
 */
public final class LookOverlays {
    /** Overlay of lid part {@code part}: model id per condition level (1..3). */
    public record Entry(int part, Map<Integer, ResourceLocation> levels) {
        @Nullable
        public ResourceLocation level(int level) {
            return levels.get(level);
        }
    }

    private LookOverlays() {}

    /**
     * Parses {@code condition_overlays} of a look with {@code partCount} lid parts. A bad entry (part out of range,
     * level not 1..3, bad model id) is skipped with a warning; a missing field gives an empty list.
     */
    public static List<Entry> parse(String lookId, JsonObject look, int partCount) {
        if (!look.has("condition_overlays")) {
            return List.of();
        }
        List<Entry> out = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(look, "condition_overlays")) {
            try {
                JsonObject ov = e.getAsJsonObject();
                int part = GsonHelper.getAsInt(ov, "part");
                if (part < 0 || part >= partCount) {
                    throw new IllegalArgumentException("part " + part + " out of range (look has " + partCount + " lid parts)");
                }
                Map<Integer, ResourceLocation> levels = new HashMap<>();
                for (Map.Entry<String, JsonElement> lv : GsonHelper.getAsJsonObject(ov, "levels").entrySet()) {
                    int level = Integer.parseInt(lv.getKey().strip());
                    if (level < 1 || level > 3) {
                        throw new IllegalArgumentException("level " + level + " (expected 1..3)");
                    }
                    levels.put(level, ResourceLocation.parse(lv.getValue().getAsString()));
                }
                out.add(new Entry(part, Map.copyOf(levels)));
            } catch (Exception ex) {
                Manholes.LOGGER.warn("Manhole look '{}': skipping condition overlay {}: {}", lookId, e, ex.getMessage());
            }
        }
        return List.copyOf(out);
    }

    /**
     * 1.7.0: overlays of the static frame, per base: {@code closed} is drawn with {@code base_closed}, {@code open} with
     * {@code base_open}. Model id per condition level (1..3); empty maps = none.
     */
    public record BaseOverlays(Map<Integer, ResourceLocation> closed, Map<Integer, ResourceLocation> open) {
        public static final BaseOverlays NONE = new BaseOverlays(Map.of(), Map.of());

        @Nullable
        public ResourceLocation level(boolean openBase, int level) {
            return (openBase ? open : closed).get(level);
        }

        public boolean isEmpty() {
            return closed.isEmpty() && open.isEmpty();
        }
    }

    /**
     * Parses {@code base_condition_overlays} ({@code {"closed": {"1": model, ...}, "open": {...}}}). A missing field or
     * a missing / malformed side gives no overlays for it; a bad level or model id is skipped with a warning.
     */
    public static BaseOverlays parseBase(String lookId, JsonObject look) {
        if (!look.has("base_condition_overlays")) {
            return BaseOverlays.NONE;
        }
        if (!look.get("base_condition_overlays").isJsonObject()) {
            Manholes.LOGGER.warn("Manhole look '{}': base_condition_overlays is not an object; ignored", lookId);
            return BaseOverlays.NONE;
        }
        JsonObject o = look.getAsJsonObject("base_condition_overlays");
        return new BaseOverlays(baseSide(lookId, o, "closed"), baseSide(lookId, o, "open"));
    }

    private static Map<Integer, ResourceLocation> baseSide(String lookId, JsonObject o, String side) {
        if (!o.has(side)) {
            return Map.of();
        }
        if (!o.get(side).isJsonObject()) {
            Manholes.LOGGER.warn("Manhole look '{}': base_condition_overlays.{} is not an object; skipped", lookId, side);
            return Map.of();
        }
        Map<Integer, ResourceLocation> levels = new HashMap<>();
        for (Map.Entry<String, JsonElement> lv : o.getAsJsonObject(side).entrySet()) {
            try {
                int level = Integer.parseInt(lv.getKey().strip());
                if (level < 1 || level > 3) {
                    throw new IllegalArgumentException("level " + level + " (expected 1..3)");
                }
                levels.put(level, ResourceLocation.parse(lv.getValue().getAsString()));
            } catch (Exception ex) {
                Manholes.LOGGER.warn("Manhole look '{}': skipping base overlay {}.{}: {}", lookId, side, lv.getKey(), ex.getMessage());
            }
        }
        return Map.copyOf(levels);
    }
}
