// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.client.ManholesClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.RenderTypeHelper;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws a manhole cover from its look ({@link CoverLooks}): {@code base_open} / {@code base_closed} plus the lid parts,
 * each moved from its closed pose (t = 0) to its {@code open} pose (t = 1) with ease-in-out over the look's
 * {@code duration_ticks}, starting at {@link ManholeBlockEntity#animStart}. Everything is in the model's north-facing
 * frame, then rotated by the block's {@code facing} like the blockstate's {@code y} rotation. The block itself renders
 * nothing ({@code RenderShape.ENTITYBLOCK_ANIMATED}); without a usable look the blockstate's own (static) model is drawn.
 */
public final class ManholeCoverRenderer implements BlockEntityRenderer<ManholeBlockEntity> {
    private final BlockRenderDispatcher blocks;
    private final RandomSource random = RandomSource.create();

    public ManholeCoverRenderer(BlockEntityRendererProvider.Context ctx) {
        this.blocks = ctx.getBlockRenderDispatcher();
    }

    /** Blockstate {@code y} rotation of a facing (north 0, east 90, south 180, west 270). */
    static int yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Ease-in-out (smoothstep). */
    static float ease(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /** 0 = fully closed, 1 = fully open, for the current frame. */
    static float openness(ManholeBlockEntity be, boolean open, int duration, float partialTick) {
        float raw = 1f;
        if (ManholesClientConfig.animateCovers() && be.animStart != Long.MIN_VALUE && be.getLevel() != null) {
            raw = Mth.clamp((be.getLevel().getGameTime() - be.animStart + partialTick) / duration, 0f, 1f);
        }
        float e = ease(raw);
        return open ? e : 1f - e;
    }

    @Override
    public void render(ManholeBlockEntity be, float partialTick, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof ManholeBlock)) {
            return;
        }
        CoverLooks.Look look = CoverLooks.get(be.look());
        if (look == null) {
            draw(ps, buffers, state, blocks.getBlockModel(state), light, overlay); // static, already rotated
            return;
        }
        boolean open = state.getValue(ManholeBlock.OPEN);
        float t = openness(be, open, look.durationTicks(), partialTick);
        var models = Minecraft.getInstance().getModelManager();

        ps.pushPose();
        ps.translate(0.5f, 0f, 0.5f);
        ps.mulPose(Axis.YP.rotationDegrees(-yRot(state.getValue(ManholeBlock.FACING))));
        ps.translate(-0.5f, 0f, -0.5f);
        // The hole shows as soon as the lid starts to move and until it's back in place.
        boolean openBase = t > 0f;
        ResourceLocation base = openBase ? look.baseOpen() : look.baseClosed();
        draw(ps, buffers, state, models.getModel(base), light, overlay);
        // 1.6.0: condition overlay (rust 1..3; homes are always 0), drawn with its part's transform.
        int condition = ManholesClientConfig.showConditionOverlays() ? be.rust() : 0;
        if (condition > 0) {
            // 1.7.0: the static frame's overlay, for whichever base is drawn (same facing rotation and light).
            ResourceLocation bov = look.baseOverlay(openBase, condition);
            if (bov != null && CoverLooks.overlayUsable(bov)) {
                draw(ps, buffers, state, models.getModel(bov), light, overlay);
            }
        }
        for (int i = 0; i < look.parts().size(); i++) {
            CoverLooks.Part p = look.parts().get(i);
            ps.pushPose();
            // Same convention as vanilla element rotation (see LidMath); any angle, not just +-45.
            ps.mulPoseMatrix(LidMath.partMatrix(p.axis() == null ? 'y' : p.axis().name().charAt(0),
                    p.axis() == null ? 0f : p.angle(), p.origin(), p.translate(), t));
            draw(ps, buffers, state, models.getModel(p.model()), light, overlay);
            if (condition > 0) {
                ResourceLocation ov = look.overlay(i, condition);
                if (ov != null && CoverLooks.overlayUsable(ov)) {
                    draw(ps, buffers, state, models.getModel(ov), light, overlay);
                }
            }
            ps.popPose();
        }
        ps.popPose();
    }

    /**
     * Draws a model with the render type(s) it declares ({@code "render_type"} in the model JSON): solid / undeclared
     * becomes cutout, {@code minecraft:translucent} stays translucent (1.7.0: the soft wood-mould condition overlays).
     */
    private void draw(PoseStack ps, MultiBufferSource buffers, BlockState state, BakedModel model, int light, int overlay) {
        random.setSeed(42L);
        for (RenderType declared : model.getRenderTypes(state, random, ModelData.EMPTY)) {
            // A part without its own render_type falls back to the block's (solid): draw it cutout so ladders,
            // trapdoors and grates keep their holes. Opaque textures look the same either way.
            RenderType rt = declared == RenderType.solid() ? RenderType.cutout() : declared;
            blocks.getModelRenderer().renderModel(ps.last(), buffers.getBuffer(RenderTypeHelper.getEntityRenderType(rt, false)),
                    state, model, 1f, 1f, 1f, light, overlay, ModelData.EMPTY, rt);
        }
    }


    /** Covers are sparse; draw them as far as normal chunks usually go. */
    @Override
    public int getViewDistance() {
        return 256;
    }
}
