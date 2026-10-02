// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Terrain without FTB Chunks: a vanilla-map-style colour image (top block {@link MapColor}, shaded by the height step to
 * the north, water shaded by depth) of the chunks the client has loaded around the player. It is built once when the
 * travel screen opens and kept in a {@link DynamicTexture} until it closes. Chunks that are not loaded stay transparent.
 */
public final class LoadedChunksTerrain implements MapTerrain {
    private static final int MAX_RADIUS = 16;
    private static final int MAX_RADIUS_CEILING = 10;

    @Nullable
    private DynamicTexture texture;
    @Nullable
    private ResourceKey<Level> builtFor;
    private int originX;
    private int originZ;
    private int size;

    @Override
    public void open(ResourceKey<Level> dimension) {
        close();
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || !level.dimension().equals(dimension)) {
            return;
        }
        boolean ceiling = level.dimensionType().hasCeiling();
        int radius = Math.max(2, Math.min(ceiling ? MAX_RADIUS_CEILING : MAX_RADIUS, mc.options.getEffectiveRenderDistance()));
        int pcx = mc.player.blockPosition().getX() >> 4;
        int pcz = mc.player.blockPosition().getZ() >> 4;
        size = (2 * radius + 1) * 16;
        originX = (pcx - radius) * 16;
        originZ = (pcz - radius) * 16;
        int minY = level.getMinBuildHeight();
        int startY = ceiling ? Math.min(level.getMaxBuildHeight() - 1, mc.player.blockPosition().getY() + 24) : 0;
        NativeImage img = new NativeImage(NativeImage.Format.RGBA, size, size, true);
        int[] north = new int[size]; // heights of the previous row, for the slope shading
        Arrays.fill(north, Integer.MIN_VALUE);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int wx = originX + x;
                int wz = originZ + z;
                LevelChunk chunk = level.getChunkSource().getChunk(wx >> 4, wz >> 4, ChunkStatus.FULL, false);
                if (chunk == null) {
                    north[x] = Integer.MIN_VALUE;
                    continue;
                }
                int y = ceiling ? surfaceBelow(chunk, p, wx, startY, wz, minY)
                        : chunk.getHeight(Heightmap.Types.WORLD_SURFACE, wx & 15, wz & 15);
                int color = 0;
                int height = y;
                for (int tries = 0; tries < 24 && y >= minY; tries++, y--) {
                    BlockState st = chunk.getBlockState(p.set(wx, y, wz));
                    MapColor col = st.getMapColor(level, p);
                    if (col == MapColor.NONE) {
                        continue;
                    }
                    MapColor.Brightness b;
                    if (!st.getFluidState().isEmpty() && col == MapColor.WATER) {
                        int depth = 0;
                        while (depth < 12 && y - depth - 1 >= minY
                                && !chunk.getBlockState(p.set(wx, y - depth - 1, wz)).getFluidState().isEmpty()) {
                            depth++;
                        }
                        b = depth <= 1 ? MapColor.Brightness.HIGH : depth <= 4 ? MapColor.Brightness.NORMAL : MapColor.Brightness.LOW;
                    } else {
                        int n = north[x];
                        b = n == Integer.MIN_VALUE || n == y ? MapColor.Brightness.NORMAL
                                : y > n ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                    }
                    color = col.calculateRGBColor(b);
                    height = y;
                    break;
                }
                north[x] = height;
                img.setPixelRGBA(x, z, color);
            }
        }
        texture = new DynamicTexture(img);
        texture.upload();
        builtFor = dimension;
    }

    /** Ceiling dimensions: the first solid block under the first air gap below startY (the floor the player walks on). */
    private static int surfaceBelow(LevelChunk chunk, BlockPos.MutableBlockPos p, int x, int startY, int z, int minY) {
        boolean air = false;
        for (int y = startY; y > minY; y--) {
            BlockState st = chunk.getBlockState(p.set(x, y, z));
            if (st.isAir()) {
                air = true;
            } else if (air) {
                return y;
            }
        }
        return minY;
    }

    @Override
    public void draw(GuiGraphics g, ResourceKey<Level> dimension, double worldLeft, double worldTop, double scale,
            int left, int top, int right, int bottom) {
        if (texture == null || !dimension.equals(builtFor)) {
            return;
        }
        float x0 = (float) (left + (originX - worldLeft) * scale);
        float y0 = (float) (top + (originZ - worldTop) * scale);
        MapTerrain.blitTexture(g, texture.getId(), x0, y0, (float) (x0 + size * scale), (float) (y0 + size * scale),
                0, 0, 1, 1, scale < 1);
    }

    @Override
    public void close() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
        builtFor = null;
    }
}
