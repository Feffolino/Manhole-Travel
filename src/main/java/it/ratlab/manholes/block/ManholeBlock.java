// SPDX-License-Identifier: MIT
package it.ratlab.manholes.block;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.travel.ManholeInteraction;
import it.ratlab.manholes.travel.Owners;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A flat (2/16 high) travel-point cover. Every look is its own block ({@code manholes:manhole}, {@code hatch},
 * {@code grate}, {@code cave_hole}, {@code city_manhole}, {@code home_manhole}); {@link #look()} names the client look
 * file {@code assets/<ns>/looks/<look>.json}. {@code home} = player-placeable, breakable variant.
 */
public class ManholeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** 1x1 shape: {@code manholes:hatch} (its model fits the cell). */
    private static final VoxelShape SHAPE_CELL = Block.box(0, 0, 0, 16, 2, 16);
    /**
     * 1.7.0: every other cover's outline / collision is the model's full visual footprint, 4 px past the cell on each
     * side. Vanilla collision handles it (BlockCollisions scans the entity box inflated by 1, and a shape outside the
     * unit cube sets {@code hasLargeCollisionShape}).
     */
    private static final VoxelShape SHAPE_WIDE = Block.box(-4, 0, -4, 20, 2, 20);

    public static final MapCodec<ManholeBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            com.mojang.serialization.Codec.STRING.fieldOf("look").forGetter(b -> b.look),
            com.mojang.serialization.Codec.BOOL.fieldOf("home").forGetter(b -> b.home),
            propertiesCodec()).apply(i, ManholeBlock::new));

    private final String look;
    private final boolean home;

    /**
     * True while {@link ManholeBlockEntity#swapBlock} replaces a cover with another one: the node must survive the
     * removal of the old block and the fresh block entity must not register a new node. Server thread only.
     */
    static boolean swapping;

    public ManholeBlock(String look, boolean home, Properties properties) {
        super(properties);
        this.look = look;
        this.home = home;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, home));
    }

    public boolean isHome() {
        return home;
    }

    /** Look id of this block ({@code manhole}, {@code hatch}, {@code cave}, {@code mypack:drain}, ...). */
    public String look() {
        return look;
    }

    /** Every registered cover block (any mod), in registry order. */
    public static java.util.List<ManholeBlock> all() {
        java.util.List<ManholeBlock> out = new java.util.ArrayList<>();
        for (Block b : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            if (b instanceof ManholeBlock m) {
                out.add(m);
            }
        }
        return out;
    }

    /** Registry id of this block as a string ({@code manholes:hatch}). */
    public String id() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this).toString();
    }

    /**
     * Finds a cover block from a look id ({@code hatch}, {@code cave}) or a block id ({@code manholes:hatch},
     * {@code manholes:cave_hole}, {@code mypack:drain_cover}). Case-insensitive. Returns null if nothing matches.
     */
    @Nullable
    public static ManholeBlock resolve(@Nullable String lookOrId) {
        if (lookOrId == null) {
            return null;
        }
        String s = lookOrId.strip().toLowerCase(java.util.Locale.ROOT);
        if (s.isEmpty() || s.length() > 128) {
            return null;
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(s);
        if (rl == null) {
            return null;
        }
        if (s.contains(":")) {
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (b instanceof ManholeBlock m) {
                return m;
            }
        }
        // A look id: "manholes:" is implied / dropped, other namespaces are kept.
        String look = !s.contains(":") || rl.getNamespace().equals("manholes") ? rl.getPath() : rl.toString();
        for (ManholeBlock m : all()) {
            if (m.look.equals(look)) {
                return m;
            }
        }
        if (!s.contains(":")) {
            Block b = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                    .getOptional(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("manholes", s)).orElse(null);
            if (b instanceof ManholeBlock m) {
                return m;
            }
        }
        return null;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(OPEN, home);
    }

    /** Outline, collision and interaction shape of this cover (see {@link #SHAPE_WIDE}). */
    public VoxelShape coverShape() {
        return "hatch".equals(look) ? SHAPE_CELL : SHAPE_WIDE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coverShape();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coverShape();
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return coverShape();
    }

    /**
     * 1.7.0: fluids never replace a cover (bucket emptying, flowing fluids). Flowing fluids also treat it as a wall
     * because the block is {@code forceSolidOn} (FlowingFluid.canHoldFluid returns {@code !state.blocksMotion()}).
     */
    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        // 1.7.2: at rest the cover is in the chunk mesh (client CoverBakedModel wraps the blockstate model and builds
        // the look's quads); ManholeCoverRenderer only draws while the lid is moving. BaseEntityBlock's default is
        // INVISIBLE, so this must stay MODEL.
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManholeBlockEntity(pos, state);
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!home && ManholesConfig.b(ManholesConfig.UNBREAKABLE)) {
            return 0.0f;
        }
        if (home && !mayBreakHome(player, level, pos)) {
            return 0.0f;
        }
        return super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof ManholeBlockEntity be) {
            NodeRecord r = be.ensureRegistered();
            if (home && r != null && placer instanceof ServerPlayer sp) {
                // 1.5.0: a personal travel point of the placer, private until he shares it with his team.
                be.setOwner(sp.getUUID(), sp.getGameProfile().getName(), false);
            }
        }
    }

    /**
     * Home manholes: in survival only the owner breaks them (or anyone with {@code home.anyoneCanBreak}); unowned
     * homes and creative players always can. Works on both sides (the client copy of the block entity has no owner, so
     * the client shows progress; the server's {@code BreakEvent} check has the last word).
     */
    public static boolean mayBreakHome(Player player, BlockGetter level, BlockPos pos) {
        if (player.getAbilities().instabuild || ManholesConfig.b(ManholesConfig.HOME_ANYONE_CAN_BREAK)) {
            return true;
        }
        if (!(level.getBlockEntity(pos) instanceof ManholeBlockEntity be) || !(be.getLevel() instanceof ServerLevel)) {
            return true;
        }
        return be.owner() == null || be.owner().equals(player.getUUID());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!swapping && !state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof ManholeBlockEntity be && be.nodeId() != null) {
            ManholeData.get(sl.getServer()).removeNode(be.nodeId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            // A pry tool never swings (CONSUME doesn't): holding right-click to pry must not play the swing animation.
            return it.ratlab.manholes.item.PryTools.isPryTool(stack) ? ItemInteractionResult.CONSUME : ItemInteractionResult.SUCCESS;
        }
        return ManholeInteraction.useItem((ServerPlayer) player, (ServerLevel) level, pos, state, hand, stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return ManholeInteraction.useEmpty((ServerPlayer) player, (ServerLevel) level, pos, state);
    }
}
