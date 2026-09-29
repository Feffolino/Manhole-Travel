// SPDX-License-Identifier: MIT
package it.ratlab.manholes.data;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** One entry of the global node registry: where a manhole is and how it's called. */
public final class NodeRecord {
    public final UUID id;
    public ResourceKey<Level> dimension;
    public BlockPos pos;
    /** Raw name from the block entity ("" = none). Plain text, or a JSON text component. */
    public String name = "";
    /** Fallback name (biome + coords), JSON text component, computed when the node is registered. */
    public String fallback = "";
    /** Spawn rule that generated it ("" = hand-placed / NBT structure). */
    public String ruleId = "";
    /** Structure it was generated in ("" = none). */
    public String structureId = "";
    public boolean home;
    public boolean generated;
    /** Look id of the cover (see ManholeBlockEntity#look), "" = unknown / default. */
    public String look = "";
    /** Home manholes (1.5.0): the player who placed (or claimed) it; null = unowned (legacy / NBT / API), claimable. */
    @Nullable
    public UUID owner;
    /** Owner's name when it was placed / last used by the owner ("" = unknown). */
    public String ownerName = "";
    /** Home manholes: shared with the owner's current FTB team. Unowned homes always count as shared. */
    public boolean shared;

    public NodeRecord(UUID id, ResourceKey<Level> dimension, BlockPos pos) {
        this.id = id;
        this.dimension = dimension;
        this.pos = pos.immutable();
    }

    /** Look id, never empty. */
    public String lookOrDefault() {
        return look.isEmpty() ? (home ? "home_manhole" : "city") : look;
    }

    public Component displayName(HolderLookup.Provider registries) {
        if (!name.isEmpty()) {
            return Names.parse(name, registries);
        }
        if (!fallback.isEmpty()) {
            return Names.parse(fallback, registries);
        }
        return Component.translatable("manholes.name.coords", pos.getX(), pos.getY(), pos.getZ());
    }

    /** Short id used in commands and messages (first 8 hex chars). */
    public String shortId() {
        return id.toString().substring(0, 8);
    }

    /** KubeJS stage / scoreboard tag given when this node is opened. */
    public String stageName() {
        if (!ruleId.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(ruleId);
            String path = rl != null ? rl.getPath() : ruleId;
            return "manholes_opened_" + path.replace('/', '_').replace(':', '_');
        }
        return "manholes_opened_" + id;
    }

    CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("dim", dimension.location().toString());
        t.putLong("pos", pos.asLong());
        t.putString("name", name);
        t.putString("fallback", fallback);
        t.putString("rule", ruleId);
        t.putString("structure", structureId);
        t.putBoolean("home", home);
        t.putBoolean("generated", generated);
        t.putString("look", look);
        if (owner != null) {
            t.putUUID("owner", owner);
            t.putString("owner_name", ownerName);
        }
        t.putBoolean("shared", shared);
        return t;
    }

    @Nullable
    static NodeRecord load(CompoundTag t) {
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        if (dim == null || !t.hasUUID("id")) {
            return null;
        }
        NodeRecord r = new NodeRecord(t.getUUID("id"), ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(t.getLong("pos")));
        r.name = t.getString("name");
        r.fallback = t.getString("fallback");
        r.ruleId = t.getString("rule");
        r.structureId = t.getString("structure");
        r.home = t.getBoolean("home");
        r.generated = t.getBoolean("generated");
        r.look = t.getString("look");
        r.owner = t.hasUUID("owner") ? t.getUUID("owner") : null;
        r.ownerName = t.getString("owner_name");
        r.shared = t.getBoolean("shared");
        return r;
    }
}
