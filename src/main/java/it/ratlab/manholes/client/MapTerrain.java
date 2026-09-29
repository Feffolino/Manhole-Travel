// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * Terrain under the travel map. The screen asks the source to draw the world rectangle it shows; a source maps world
 * block (x, z) to screen (left + (x - worldLeft) * scale, top + (z - worldTop) * scale). Nothing drawn = the dark
 * background shows (unexplored / unloaded).
 */
public interface MapTerrain {
    /** Called when the screen opens (build caches). */
    default void open(ResourceKey<Level> dimension) {}

    void draw(GuiGraphics g, ResourceKey<Level> dimension, double worldLeft, double worldTop, double scale,
            int left, int top, int right, int bottom);

    /** Called when the screen closes (free GPU memory). */
    default void close() {}

    /** Draws a GL texture (by id) into a screen rectangle; {@code linear} = smooth filtering when shrunk. */
    static void blitTexture(GuiGraphics g, int textureId, float x0, float y0, float x1, float y1,
            float u0, float v0, float u1, float v1, boolean linear) {
        RenderSystem.bindTexture(textureId);
        int filter = linear ? GL11.GL_LINEAR : GL11.GL_NEAREST;
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, filter);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        b.addVertex(m, x0, y0, 0).setUv(u0, v0);
        b.addVertex(m, x0, y1, 0).setUv(u0, v1);
        b.addVertex(m, x1, y1, 0).setUv(u1, v1);
        b.addVertex(m, x1, y0, 0).setUv(u1, v0);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
