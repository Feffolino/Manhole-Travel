// SPDX-License-Identifier: MIT
package it.ratlab.manholes.compat.ftbchunks;

import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.client.MapTerrain;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Travel-map terrain from FTB Chunks' own client map (INTERNAL API: {@code client.map.MapManager / MapDimension /
 * MapRegion}, checked against 2101.1.22). Each region is 512x512 blocks with a 512x512 texture that FTB Chunks renders
 * and uploads lazily (like its large map's {@code MapTileWidget}); unexplored pixels are transparent, so they stay dark.
 * Any linkage or runtime error disables this source for the session and the screen falls back to
 * {@link it.ratlab.manholes.client.LoadedChunksTerrain}.
 */
public final class FTBChunksTerrain implements MapTerrain {
    /** Region textures requested per frame at most (each is 1 MB of GPU memory, FTB Chunks releases stale ones). */
    private static final int MAX_REGIONS = 96;
    private static final double REGION = 512.0;

    private final MapTerrain fallback;
    private boolean broken;

    public FTBChunksTerrain(MapTerrain fallback) {
        this.fallback = fallback;
    }

    @Override
    public void open(ResourceKey<Level> dimension) {
        fallback.open(dimension);
    }

    @Override
    public void close() {
        fallback.close();
    }

    @Override
    public void draw(GuiGraphics g, ResourceKey<Level> dimension, double worldLeft, double worldTop, double scale,
            int left, int top, int right, int bottom) {
        if (!broken) {
            try {
                if (drawFtb(g, dimension, worldLeft, worldTop, scale, left, top, right, bottom)) {
                    return;
                }
            } catch (Throwable t) { // LinkageError from another FTB Chunks version, or anything else
                broken = true;
                Manholes.LOGGER.warn("FTB Chunks map textures unavailable, the travel map uses loaded chunks instead", t);
            }
        }
        fallback.draw(g, dimension, worldLeft, worldTop, scale, left, top, right, bottom);
    }

    private static boolean drawFtb(GuiGraphics g, ResourceKey<Level> dimension, double worldLeft, double worldTop, double scale,
            int left, int top, int right, int bottom) {
        Optional<MapManager> manager = MapManager.getInstance();
        if (manager.isEmpty()) {
            return false;
        }
        MapDimension dim = manager.get().getDimension(dimension);
        dim.getRegions(); // reads the region index once
        Collection<MapRegion> regions = dim.getLoadedRegions();
        double worldRight = worldLeft + (right - left) / scale;
        double worldBottom = worldTop + (bottom - top) / scale;
        double cx = (worldLeft + worldRight) / 2;
        double cz = (worldTop + worldBottom) / 2;
        List<MapRegion> visible = new ArrayList<>();
        for (MapRegion r : regions) {
            double x0 = r.pos.x() * REGION;
            double z0 = r.pos.z() * REGION;
            if (x0 + REGION > worldLeft && x0 < worldRight && z0 + REGION > worldTop && z0 < worldBottom) {
                visible.add(r);
            }
        }
        if (visible.size() > MAX_REGIONS) {
            visible.sort((a, b) -> Double.compare(dist2(a, cx, cz), dist2(b, cx, cz)));
            visible = visible.subList(0, MAX_REGIONS);
        }
        boolean shrink = REGION * scale < REGION;
        for (MapRegion r : visible) {
            int id = r.getRenderedMapImageTextureId(); // schedules rendering / upload when needed
            if (!r.isMapImageLoaded()) {
                continue;
            }
            float x0 = (float) (left + (r.pos.x() * REGION - worldLeft) * scale);
            float y0 = (float) (top + (r.pos.z() * REGION - worldTop) * scale);
            float s = (float) (REGION * scale);
            MapTerrain.blitTexture(g, id, x0, y0, x0 + s, y0 + s, 0, 0, 1, 1, shrink);
        }
        return true;
    }

    private static double dist2(MapRegion r, double x, double z) {
        double dx = r.pos.x() * REGION + REGION / 2 - x;
        double dz = r.pos.z() * REGION + REGION / 2 - z;
        return dx * dx + dz * dz;
    }
}
