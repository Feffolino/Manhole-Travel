// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.data.Names;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * A spawn rule. It's also the builder handed to KubeJS: every setter returns {@code this}.
 * See SUMMARY.md for the JSON schema.
 */
public final class SpawnRule {
    public enum Type { STRUCTURE, SCATTER }

    final ResourceLocation id;
    Type type;
    boolean builtin;
    // structure
    IdMatcher structures = IdMatcher.of("*");
    IdMatcher exclude = IdMatcher.ANY;
    double chance = 1.0;
    int maxPerStructure = 1;
    boolean surfaceOnly = true;
    IdMatcher avoidBlocks = IdMatcher.ANY;
    /** Clearance (blocks) the spot keeps from every structure's bounding box (1.5.0; before: shrank the box). */
    int margin = 0;
    /** Ring around the structure (1.5.0): horizontal distance from the bounding box's edge, min..max blocks. */
    int minOffset = 6;
    int maxOffset = 24;
    boolean nameFromStructure;
    // shared
    IdMatcher onBlocks = IdMatcher.ANY;
    /** Raw name (plain text or JSON component), null = none. */
    @Nullable
    String name;
    /** Cover block the rule places (e.g. manholes:hatch), null = the default manholes:city_manhole. */
    @Nullable
    ManholeBlock block;
    /** -1 = use the config default. */
    int minDistance = -1;
    // scatter
    double chancePerChunk = 0.01;
    List<String> dimensions = new ArrayList<>();
    IdMatcher biomes = IdMatcher.ANY;

    public SpawnRule(ResourceLocation id, Type type) {
        this.id = id;
        this.type = type;
    }

    public ResourceLocation id() {
        return id;
    }

    public Type type() {
        return type;
    }

    /** Look id of the cover block this rule places ({@code hatch}), or null (default manholes:city_manhole). */
    @Nullable
    public String look() {
        return block == null ? null : block.look();
    }

    /** Cover block this rule places, or null (default manholes:city_manhole). */
    @Nullable
    public ManholeBlock block() {
        return block;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    // ---------------------------------------------------------------- fluent setters (KubeJS builder)

    public SpawnRule structures(String... ids) { this.structures = IdMatcher.of(ids); return this; }
    public SpawnRule exclude(String... ids) { this.exclude = IdMatcher.of(ids); return this; }
    public SpawnRule chance(double chance) { this.chance = chance; return this; }
    public SpawnRule maxPerStructure(int max) { this.maxPerStructure = Math.max(1, max); return this; }
    public SpawnRule surfaceOnly(boolean surfaceOnly) { this.surfaceOnly = surfaceOnly; return this; }
    public SpawnRule onBlocks(String... ids) { this.onBlocks = IdMatcher.of(ids); return this; }
    public SpawnRule avoidBlocks(String... ids) { this.avoidBlocks = IdMatcher.of(ids); return this; }
    public SpawnRule margin(int margin) { this.margin = Math.max(0, margin); return this; }
    /** Ring around the structure: min..max blocks from its bounding box's edge (defaults 6, 24). */
    public SpawnRule offset(int min, int max) {
        this.minOffset = Math.max(0, Math.min(min, max));
        this.maxOffset = Math.max(0, Math.max(min, max));
        return this;
    }

    public int minOffset() {
        return minOffset;
    }

    public int maxOffset() {
        return maxOffset;
    }

    public int margin() {
        return margin;
    }
    /** Plain text, or a JSON text component string. */
    public SpawnRule name(String name) { this.name = name == null || name.isEmpty() ? null : name; return this; }
    /**
     * Cover block the rule places: a look id ({@code 'hatch'}, {@code 'cave'}) or a block id ({@code 'manholes:hatch'},
     * {@code 'manholes:cave_hole'}); empty = the default manholes:city_manhole. Throws if no cover block matches.
     */
    public SpawnRule block(String blockOrLook) {
        if (blockOrLook == null || blockOrLook.isBlank()) {
            this.block = null;
            return this;
        }
        ManholeBlock b = ManholeBlock.resolve(blockOrLook);
        if (b == null) {
            throw new IllegalArgumentException("Unknown manhole cover block or look '" + blockOrLook + "'");
        }
        this.block = b;
        return this;
    }

    /** Same as {@link #block(String)} (the 1.3.0 name). */
    public SpawnRule look(String look) {
        return block(look);
    }
    public SpawnRule nameFromStructure(boolean b) { this.nameFromStructure = b; return this; }
    public SpawnRule minDistance(int d) { this.minDistance = d; return this; }
    public SpawnRule chancePerChunk(double c) { this.chancePerChunk = c; return this; }
    public SpawnRule dimensions(String... dims) { this.dimensions = new ArrayList<>(List.of(dims)); return this; }
    public SpawnRule biomes(String... ids) { this.biomes = IdMatcher.of(ids); return this; }

    // ---------------------------------------------------------------- JSON

    public static SpawnRule fromJson(ResourceLocation id, JsonObject o) {
        String t = GsonHelper.getAsString(o, "type", "structure");
        Type type = switch (t) {
            case "structure", "manholes:structure" -> Type.STRUCTURE;
            case "scatter", "manholes:scatter" -> Type.SCATTER;
            default -> throw new JsonParseException("Unknown spawn rule type '" + t + "' (expected 'structure' or 'scatter')");
        };
        SpawnRule r = new SpawnRule(id, type);
        if (o.has("structures")) r.structures = IdMatcher.parse(o.get("structures"));
        if (o.has("exclude")) r.exclude = IdMatcher.parse(o.get("exclude"));
        r.chance = GsonHelper.getAsDouble(o, "chance", 1.0);
        r.maxPerStructure = Math.max(1, GsonHelper.getAsInt(o, "max_per_structure", 1));
        if (o.has("placement")) {
            JsonObject p = GsonHelper.getAsJsonObject(o, "placement");
            r.surfaceOnly = GsonHelper.getAsBoolean(p, "surface_only", true);
            if (p.has("on_blocks")) r.onBlocks = IdMatcher.parse(p.get("on_blocks"));
            if (p.has("avoid_blocks")) r.avoidBlocks = IdMatcher.parse(p.get("avoid_blocks"));
            r.margin = Math.max(0, GsonHelper.getAsInt(p, "margin", 0));
        }
        JsonElement off = o.has("offset") ? o.get("offset") : o.has("placement") && o.getAsJsonObject("placement").has("offset")
                ? o.getAsJsonObject("placement").get("offset") : null;
        if (off != null) {
            if (off.isJsonArray() && off.getAsJsonArray().size() == 2) {
                r.offset(off.getAsJsonArray().get(0).getAsInt(), off.getAsJsonArray().get(1).getAsInt());
            } else if (off.isJsonPrimitive()) {
                r.offset(off.getAsInt(), off.getAsInt());
            } else {
                throw new JsonParseException("'offset' must be [min, max] or a number");
            }
        }
        if (o.has("on_blocks")) r.onBlocks = IdMatcher.parse(o.get("on_blocks"));
        if (o.has("avoid_blocks")) r.avoidBlocks = IdMatcher.parse(o.get("avoid_blocks"));
        if (o.has("name")) r.name = Names.toRaw(o.get("name"));
        r.nameFromStructure = GsonHelper.getAsBoolean(o, "name_from_structure", false);
        if (o.has("block") || o.has("look")) {
            try {
                r.block(GsonHelper.getAsString(o, o.has("block") ? "block" : "look"));
            } catch (IllegalArgumentException ex) {
                throw new JsonParseException(ex.getMessage());
            }
        }
        r.minDistance = GsonHelper.getAsInt(o, "min_distance", -1);
        r.chancePerChunk = GsonHelper.getAsDouble(o, "chance_per_chunk", 0.01);
        if (o.has("dimensions")) {
            JsonElement d = o.get("dimensions");
            if (d.isJsonArray()) {
                d.getAsJsonArray().forEach(e -> r.dimensions.add(e.getAsString()));
            } else {
                r.dimensions.add(d.getAsString());
            }
        }
        if (o.has("biomes")) r.biomes = IdMatcher.parse(o.get("biomes"));
        if (r.type == Type.SCATTER && r.onBlocks.isEmpty()) {
            throw new JsonParseException("Scatter rule needs 'on_blocks'");
        }
        return r;
    }

    @Override
    public String toString() {
        return id + "[" + type.name().toLowerCase(java.util.Locale.ROOT) + (type == Type.STRUCTURE
                ? ", structures=" + structures + ", chance=" + chance + ", offset=" + minOffset + ".." + maxOffset
                : ", chance_per_chunk=" + chancePerChunk + ", on_blocks=" + onBlocks) + (block != null ? ", block=" + block.id() : "") + (builtin ? ", builtin" : "") + "]";
    }
}
