// SPDX-License-Identifier: MIT
package it.ratlab.manholes.client.cover;

import com.mojang.math.Transformation;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.client.ManholesClientConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1.7.2: the cover at rest (fully open or fully closed, no animation running) is part of the chunk mesh, so it is lit
 * by the terrain pipeline like the blocks around it (shader packs / flashlight mods light entity geometry differently).
 * Wraps the blockstate model of every {@link ManholeBlock} state (see {@link CoverEvents}).
 * <p>
 * With a usable look ({@link CoverLooks}) the quads are the look's {@code base_open} / {@code base_closed}, each lid
 * part at its rest pose (t = 1 open, t = 0 closed, same {@link LidMath} transform and facing rotation as
 * {@link ManholeCoverRenderer}) and the condition overlays for the cover's rust level (model data
 * {@link ManholeBlockEntity#RUST}). Every part keeps the render type its model declares (solid / undeclared becomes
 * cutout, like the renderer). While the block entity says it's animating ({@link ManholeBlockEntity#ANIMATING}) the
 * mesh is empty and the renderer draws the moving cover. Without a usable look the blockstate's own model is used.
 * Quads are only returned for {@code side == null}: the moved parts no longer sit on the faces their cull faces name.
 */
public final class CoverBakedModel extends BakedModelWrapper<BakedModel> {
    private static final Direction[] SIDES_AND_NULL = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST, null};

    private record Key(String look, boolean open, Direction facing, int condition) {}

    private record Mesh(ChunkRenderTypeSet types, Map<RenderType, List<BakedQuad>> byType, List<BakedQuad> all) {}

    /** Per bake (a new wrapper is made on every resource reload, so the cache never outlives the models). */
    private final Map<Key, Mesh> cache = new ConcurrentHashMap<>();

    public CoverBakedModel(BakedModel original) {
        super(original);
    }

    @Nullable
    private static CoverLooks.Look lookOf(@Nullable BlockState state) {
        return state != null && state.getBlock() instanceof ManholeBlock b ? CoverLooks.get(b.look()) : null;
    }

    static boolean animating(ModelData data) {
        return Boolean.TRUE.equals(data.get(ManholeBlockEntity.ANIMATING));
    }

    static int condition(ModelData data) {
        Integer r = data.get(ManholeBlockEntity.RUST);
        return r != null && ManholesClientConfig.showConditionOverlays() ? Math.max(0, Math.min(3, r)) : 0;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
            @Nullable RenderType renderType) {
        CoverLooks.Look look = lookOf(state);
        if (look == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        if (side != null || animating(data)) {
            return List.of();
        }
        Mesh m = mesh(state, look, condition(data));
        return renderType == null ? m.all() : m.byType().getOrDefault(renderType, List.of());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        CoverLooks.Look look = lookOf(state);
        if (look == null) {
            return super.getRenderTypes(state, rand, data);
        }
        return animating(data) ? ChunkRenderTypeSet.none() : mesh(state, look, condition(data)).types();
    }

    /** The part models are flat-lit (ambientocclusion false) like the renderer drew them; keep it that way. */
    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        return lookOf(state) == null ? super.useAmbientOcclusion(state, data, renderType) : TriState.FALSE;
    }

    private Mesh mesh(BlockState state, CoverLooks.Look look, int condition) {
        Key key = new Key(look.id(), state.getValue(ManholeBlock.OPEN), state.getValue(ManholeBlock.FACING), condition);
        return cache.computeIfAbsent(key, k -> build(state, look, condition));
    }

    private static Mesh build(BlockState state, CoverLooks.Look look, int condition) {
        ModelManager models = Minecraft.getInstance().getModelManager();
        boolean open = state.getValue(ManholeBlock.OPEN);
        float t = open ? 1f : 0f;
        Matrix4f facing = new Matrix4f()
                .translate(0.5f, 0f, 0.5f)
                .rotateY((float) Math.toRadians(-ManholeCoverRenderer.yRot(state.getValue(ManholeBlock.FACING))))
                .translate(-0.5f, 0f, -0.5f);
        Map<RenderType, List<BakedQuad>> byType = new LinkedHashMap<>();
        add(byType, state, models.getModel(open ? look.baseOpen() : look.baseClosed()), facing);
        if (condition > 0) {
            ModelResourceLocation bov = look.baseOverlay(open, condition);
            if (bov != null && CoverLooks.overlayUsable(bov)) {
                add(byType, state, models.getModel(bov), facing);
            }
        }
        for (int i = 0; i < look.parts().size(); i++) {
            CoverLooks.Part p = look.parts().get(i);
            Matrix4f m = new Matrix4f(facing).mul(LidMath.partMatrix(p.axis() == null ? 'y' : p.axis().name().charAt(0),
                    p.axis() == null ? 0f : p.angle(), p.origin(), p.translate(), t));
            add(byType, state, models.getModel(p.model()), m);
            if (condition > 0) {
                ModelResourceLocation ov = look.overlay(i, condition);
                if (ov != null && CoverLooks.overlayUsable(ov)) {
                    add(byType, state, models.getModel(ov), m);
                }
            }
        }
        List<BakedQuad> all = new ArrayList<>();
        Map<RenderType, List<BakedQuad>> frozen = new LinkedHashMap<>();
        byType.forEach((rt, quads) -> {
            frozen.put(rt, List.copyOf(quads));
            all.addAll(quads);
        });
        return new Mesh(ChunkRenderTypeSet.of(frozen.keySet()), Map.copyOf(frozen), List.copyOf(all));
    }

    /** Adds a part model's quads (every side, same seed as the renderer), moved by {@code m}, under its render type. */
    private static void add(Map<RenderType, List<BakedQuad>> byType, BlockState state, BakedModel model, Matrix4f m) {
        var transformer = QuadTransformers.applying(new Transformation(new Matrix4f(m)));
        RandomSource rand = RandomSource.create(42L);
        for (RenderType declared : model.getRenderTypes(state, rand, ModelData.EMPTY)) {
            // Like ManholeCoverRenderer.draw: a part without its own render_type (solid) is drawn cutout.
            RenderType rt = declared == RenderType.solid() ? RenderType.cutout() : declared;
            List<BakedQuad> out = byType.computeIfAbsent(rt, k -> new ArrayList<>());
            for (Direction d : SIDES_AND_NULL) {
                rand.setSeed(42L);
                for (BakedQuad q : model.getQuads(state, d, rand, ModelData.EMPTY, declared)) {
                    BakedQuad moved = transformer.process(q);
                    out.add(new BakedQuad(moved.getVertices(), moved.getTintIndex(), turned(q.getDirection(), m),
                            moved.getSprite(), moved.isShade(), false));
                }
            }
        }
    }

    /** The face direction after the transform (shading and light sampling use it). */
    static Direction turned(Direction dir, Matrix4f m) {
        Vector3f n = m.transformDirection(new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ()));
        return Direction.getNearest(n.x, n.y, n.z);
    }
}
