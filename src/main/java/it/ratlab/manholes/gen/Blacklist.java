// SPDX-License-Identifier: MIT
package it.ratlab.manholes.gen;

import it.ratlab.manholes.ManholesConfig;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.Nullable;

/**
 * The common-config blacklists (1.5.0, {@code generation.structureBlacklist / biomeBlacklist / dimensionBlacklist}).
 * They apply to every rule (built-in, datapack, KubeJS); biomes and dimensions to scatter rules too. Matchers are
 * rebuilt only when the config list changes.
 */
public final class Blacklist {
    private static List<String> structSrc;
    private static IdMatcher structs = IdMatcher.ANY;
    private static List<String> biomeSrc;
    private static IdMatcher biomes = IdMatcher.ANY;

    private Blacklist() {}

    private static IdMatcher structures() {
        List<String> l = ManholesConfig.list(ManholesConfig.STRUCTURE_BLACKLIST);
        if (l != structSrc) {
            structSrc = l;
            structs = new IdMatcher(l);
        }
        return structs;
    }

    private static IdMatcher biomes() {
        List<String> l = ManholesConfig.list(ManholesConfig.BIOME_BLACKLIST);
        if (l != biomeSrc) {
            biomeSrc = l;
            biomes = new IdMatcher(l);
        }
        return biomes;
    }

    public static boolean dimension(ServerLevel level) {
        return ManholesConfig.list(ManholesConfig.DIMENSION_BLACKLIST).contains(level.dimension().location().toString());
    }

    public static boolean structure(Holder<Structure> holder, Registry<Structure> reg) {
        return structures().matchesExplicit(holder, reg);
    }

    /** By id (queued placements); unknown ids are not blacklisted. */
    public static boolean structure(ServerLevel level, @Nullable String structureId) {
        ResourceLocation id = structureId == null ? null : ResourceLocation.tryParse(structureId);
        if (id == null) {
            return false;
        }
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        return reg.getHolder(ResourceKey.create(Registries.STRUCTURE, id)).map(h -> structure(h, reg)).orElse(false);
    }

    public static boolean biome(Holder<net.minecraft.world.level.biome.Biome> biome, Registry<net.minecraft.world.level.biome.Biome> reg) {
        IdMatcher m = biomes();
        return !m.isEmpty() && m.matchesExplicit(biome, reg);
    }

    public static boolean biome(ServerLevel level, BlockPos pos) {
        IdMatcher m = biomes();
        return !m.isEmpty() && m.matchesExplicit(level.getBiome(pos), level.registryAccess().registryOrThrow(Registries.BIOME));
    }
}
