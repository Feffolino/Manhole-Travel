// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.math.Axis;
import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.block.ManholeBlockEntity;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.neoforged.neoforge.client.event.ModelEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Cover looks: {@code assets/<ns>/looks/<name>.json}, read from the client {@link ResourceManager} on every resource
 * reload (during {@link ModelEvent.RegisterAdditional}, so the part models they name are registered as extra models in
 * the same reload). Look id {@code manholes:hatch} is written {@code hatch}; other namespaces keep {@code ns:name}.
 * <p>
 * A look that is missing, fails to parse, or names a model that doesn't bake is not used: the renderer falls back to
 * the block's own blockstate model (the static full cover), with one log line per look.
 */
public final class CoverLooks {
    public record Part(ModelResourceLocation model, @Nullable Vector3f translate, @Nullable Axis3 axis, float angle,
            Vector3f origin) {}

    public enum Axis3 { X, Y, Z }

    /**
     * 1.6.0: condition overlay of lid part {@code part}: {@code levels[L]} (L = 1..3) is drawn over that part, with the
     * part's transform, when the cover's condition (rust) level is L.
     */
    public record Overlay(int part, Map<Integer, ModelResourceLocation> levels) {}

    public record Look(String id, ModelResourceLocation baseClosed, ModelResourceLocation baseOpen, List<Part> parts,
            int durationTicks, @Nullable ResourceLocation soundOpen, @Nullable ResourceLocation soundClose,
            List<Overlay> overlays, Map<Integer, ModelResourceLocation> baseClosedOverlays,
            Map<Integer, ModelResourceLocation> baseOpenOverlays) {
        /** 1.7.0: overlay of the base being drawn (open or closed) at condition {@code level}, or null. */
        @Nullable
        public ModelResourceLocation baseOverlay(boolean openBase, int level) {
            return (openBase ? baseOpenOverlays : baseClosedOverlays).get(level);
        }

        /** Every condition overlay model (lid parts and bases). */
        public List<ModelResourceLocation> allOverlayModels() {
            List<ModelResourceLocation> out = new ArrayList<>();
            overlays.forEach(o -> out.addAll(o.levels().values()));
            out.addAll(baseClosedOverlays.values());
            out.addAll(baseOpenOverlays.values());
            return out;
        }

        /** Overlay model of part {@code part} at condition {@code level}, or null (none declared). */
        @Nullable
        public ModelResourceLocation overlay(int part, int level) {
            for (Overlay o : overlays) {
                if (o.part() == part) {
                    ModelResourceLocation m = o.levels().get(level);
                    if (m != null) {
                        return m;
                    }
                }
            }
            return null;
        }
    }

    /** Parsed looks (id -> look), swapped whole on reload. */
    private static volatile Map<String, Look> looks = Map.of();
    /** Looks whose models all baked (checked after baking). */
    private static volatile Set<String> usable = Set.of();
    /** Condition overlay models that baked (a missing one only skips that overlay). */
    private static volatile Set<ModelResourceLocation> usableOverlays = Set.of();
    private static final Set<String> warned = Collections.synchronizedSet(new HashSet<>());

    private CoverLooks() {}

    /** Usable look, or null (renderer falls back to the static blockstate model). Logs once per missing id. */
    @Nullable
    public static Look get(String id) {
        Look l = looks.get(id);
        if (l != null && usable.contains(id)) {
            return l;
        }
        if (warned.add(id)) {
            if (l == null) {
                Manholes.LOGGER.warn("Manhole look '{}' not found (expected {}); showing the static block model instead",
                        id, file(id));
            } else {
                Manholes.LOGGER.warn("Manhole look '{}' has models that failed to load; showing the static block model instead", id);
            }
        }
        return null;
    }

    /** True if this condition overlay model baked (checked after baking). */
    public static boolean overlayUsable(ModelResourceLocation m) {
        return usableOverlays.contains(m);
    }

    public static Set<String> ids() {
        return looks.keySet();
    }

    private static String file(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : Manholes.MOD_ID + ":" + id);
        return rl == null ? id : "assets/" + rl.getNamespace() + "/looks/" + rl.getPath() + ".json";
    }

    // ---------------------------------------------------------------- loading

    static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        ResourceManager rm = Minecraft.getInstance().getResourceManager();
        Map<String, Look> fresh = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> files = rm.listResources("looks", rl -> rl.getPath().endsWith(".json"));
        for (Map.Entry<ResourceLocation, Resource> f : files.entrySet()) {
            ResourceLocation file = f.getKey();
            String name = file.getPath().substring("looks/".length(), file.getPath().length() - ".json".length());
            String id = file.getNamespace().equals(Manholes.MOD_ID) ? name : file.getNamespace() + ":" + name;
            try (Reader r = f.getValue().openAsReader()) {
                fresh.put(id, parse(id, JsonParser.parseReader(r).getAsJsonObject()));
            } catch (Exception ex) {
                Manholes.LOGGER.error("Manhole look '{}' ({}) failed to load: {}", id, file, ex.toString());
            }
        }
        Set<ModelResourceLocation> models = new HashSet<>();
        for (Look l : fresh.values()) {
            models.add(l.baseClosed());
            models.add(l.baseOpen());
            l.parts().forEach(p -> models.add(p.model()));
            models.addAll(l.allOverlayModels());
        }
        models.forEach(event::register);
        looks = Collections.unmodifiableMap(fresh);
        usable = Set.of();
        usableOverlays = Set.of();
        warned.clear();
        Manholes.LOGGER.info("Loaded {} manhole looks {} ({} part models)", fresh.size(), fresh.keySet(), models.size());
        for (String expected : List.of("manhole", "home_manhole")) {
            if (!fresh.containsKey(expected)) {
                Manholes.LOGGER.warn("Manhole look '{}' is missing ({}): those covers use the static block model, no animation",
                        expected, file(expected));
            }
        }
    }

    static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        BakedModel missing = event.getModelManager().getMissingModel();
        Map<ModelResourceLocation, BakedModel> baked = event.getModels();
        Set<String> ok = new HashSet<>();
        Set<ModelResourceLocation> okOverlays = new HashSet<>();
        for (Look l : looks.values()) {
            List<ModelResourceLocation> badOverlays = new ArrayList<>();
            for (ModelResourceLocation m : l.allOverlayModels()) {
                BakedModel b = baked.get(m);
                if (b == null || b == missing) {
                    badOverlays.add(m);
                } else {
                    okOverlays.add(m);
                }
            }
            if (!badOverlays.isEmpty()) {
                Manholes.LOGGER.warn("Manhole look '{}': condition overlays {} are missing; those overlays are skipped",
                        l.id(), badOverlays.stream().map(ModelResourceLocation::id).toList());
            }
            List<ModelResourceLocation> all = new ArrayList<>(List.of(l.baseClosed(), l.baseOpen()));
            l.parts().forEach(p -> all.add(p.model()));
            List<ModelResourceLocation> bad = new ArrayList<>();
            for (ModelResourceLocation m : all) {
                BakedModel b = baked.get(m);
                if (b == null || b == missing) {
                    bad.add(m);
                }
            }
            if (bad.isEmpty()) {
                ok.add(l.id());
            } else {
                Manholes.LOGGER.error("Manhole look '{}': models {} are missing; that look falls back to the static block model",
                        l.id(), bad.stream().map(ModelResourceLocation::id).toList());
            }
        }
        usable = Set.copyOf(ok);
        usableOverlays = Set.copyOf(okOverlays);
    }

    static Look parse(String id, JsonObject o) {
        ModelResourceLocation closed = model(GsonHelper.getAsString(o, "base_closed"));
        ModelResourceLocation open = o.has("base_open") ? model(GsonHelper.getAsString(o, "base_open")) : closed;
        List<Part> parts = new ArrayList<>();
        if (o.has("lid_parts")) {
            JsonArray arr = GsonHelper.getAsJsonArray(o, "lid_parts");
            for (JsonElement e : arr) {
                JsonObject p = e.getAsJsonObject();
                ModelResourceLocation m = model(GsonHelper.getAsString(p, "model"));
                Vector3f tr = null;
                Axis3 axis = null;
                float angle = 0;
                Vector3f origin = new Vector3f(8, 8, 8);
                if (p.has("open")) {
                    JsonObject op = GsonHelper.getAsJsonObject(p, "open");
                    if (op.has("translate")) {
                        tr = vec(GsonHelper.getAsJsonArray(op, "translate"));
                    }
                    if (op.has("rotate")) {
                        JsonObject rot = GsonHelper.getAsJsonObject(op, "rotate");
                        axis = Axis3.valueOf(GsonHelper.getAsString(rot, "axis", "y").toUpperCase(java.util.Locale.ROOT));
                        angle = GsonHelper.getAsFloat(rot, "angle", 0);
                        if (rot.has("origin")) {
                            origin = vec(GsonHelper.getAsJsonArray(rot, "origin"));
                        }
                    }
                }
                parts.add(new Part(m, tr, axis, angle, origin));
            }
        }
        int dur = Math.max(1, GsonHelper.getAsInt(o, "duration_ticks", 12));
        LookOverlays.BaseOverlays base = LookOverlays.parseBase(id, o);
        return new Look(id, closed, open, List.copyOf(parts), dur, sound(o, "sound_open"), sound(o, "sound_close"),
                overlays(id, o, parts.size()), baseOverlays(base.closed()), baseOverlays(base.open()));
    }

    private static Map<Integer, ModelResourceLocation> baseOverlays(Map<Integer, ResourceLocation> levels) {
        Map<Integer, ModelResourceLocation> out = new HashMap<>();
        levels.forEach((lv, rl) -> out.put(lv, ModelResourceLocation.standalone(rl)));
        return Map.copyOf(out);
    }

    private static List<Overlay> overlays(String id, JsonObject o, int partCount) {
        List<Overlay> out = new ArrayList<>();
        for (LookOverlays.Entry e : LookOverlays.parse(id, o, partCount)) {
            Map<Integer, ModelResourceLocation> levels = new HashMap<>();
            e.levels().forEach((lv, rl) -> levels.put(lv, ModelResourceLocation.standalone(rl)));
            out.add(new Overlay(e.part(), Map.copyOf(levels)));
        }
        return List.copyOf(out);
    }

    @Nullable
    private static ResourceLocation sound(JsonObject o, String key) {
        return o.has(key) && !GsonHelper.getAsString(o, key).isEmpty() ? ResourceLocation.parse(GsonHelper.getAsString(o, key)) : null;
    }

    private static ModelResourceLocation model(String s) {
        return ModelResourceLocation.standalone(ResourceLocation.parse(s));
    }

    private static Vector3f vec(JsonArray a) {
        if (a.size() != 3) {
            throw new IllegalArgumentException("expected [x, y, z], got " + a);
        }
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    static Quaternionf rotation(Axis3 axis, float degrees) {
        return switch (axis) {
            case X -> Axis.XP.rotationDegrees(degrees);
            case Y -> Axis.YP.rotationDegrees(degrees);
            case Z -> Axis.ZP.rotationDegrees(degrees);
        };
    }

    // ---------------------------------------------------------------- sound

    /** Client: the cover's open state just changed; play the look's sound at the start of the animation. */
    public static void onAnimStarted(ManholeBlockEntity be) {
        if (be.getLevel() == null) {
            return;
        }
        boolean open = be.getBlockState().getValue(it.ratlab.manholes.block.ManholeBlock.OPEN);
        Look l = looks.get(be.look());
        ResourceLocation s = l == null ? null : open ? l.soundOpen() : l.soundClose();
        if (s != null) {
            be.getLevel().playLocalSound(be.getBlockPos(), SoundEvent.createVariableRangeEvent(s), SoundSource.BLOCKS, 0.8f,
                    open ? 1.0f : 0.9f, false);
        }
    }

    /** For tests / debugging. */
    static Map<String, Look> snapshot() {
        return new HashMap<>(looks);
    }
}
