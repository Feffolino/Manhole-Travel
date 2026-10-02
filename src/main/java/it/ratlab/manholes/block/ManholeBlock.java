// SPDX-License-Identifier: MIT
package it.ratlab.manholes.block;

import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.Names;
import it.ratlab.manholes.data.NodeRecord;
import it.ratlab.manholes.item.PryTools;
import it.ratlab.manholes.travel.ManholeInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A flat (2/16 high) travel-point cover.
 */
public class ManholeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    private static final VoxelShape SHAPE_CELL = Block.box(0, 0, 0, 16, 2, 16);
    private static final VoxelShape SHAPE_WIDE = Block.box(-4, 0, -4, 20, 2, 20);

    private final String look;
    private final boolean home;

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

    public String look() {
        return look;
    }

    public static List<ManholeBlock> all() {
        List<ManholeBlock> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            if (b instanceof ManholeBlock m) {
                out.add(m);
            }
        }
        return out;
    }

    public String id() {
        return BuiltInRegistries.BLOCK.getKey(this).toString();
    }

    @Nullable
    public static ManholeBlock resolve(@Nullable String lookOrId) {
        if (lookOrId == null) {
            return null;
        }
        String s = lookOrId.strip().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.length() > 128) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) {
            return null;
        }
        if (s.contains(":")) {
            Block b = BuiltInRegistries.BLOCK.getOptional(rl).orElse(null);
            if (b instanceof ManholeBlock m) {
                return m;
            }
        }
        String look = !s.contains(":") || rl.getNamespace().equals("manholes") ? rl.getPath() : rl.toString();
        for (ManholeBlock m : all()) {
            if (m.look.equals(look)) {
                return m;
            }
        }
        if (!s.contains(":")) {
            Block b = BuiltInRegistries.BLOCK
                    .getOptional(new ResourceLocation("manholes", s)).orElse(null);
            if (b instanceof ManholeBlock m) {
                return m;
            }
        }
        return null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(OPEN, home);
    }

    public VoxelShape coverShape() {
        return "hatch".equals(look) ? SHAPE_CELL : SHAPE_WIDE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coverShape();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return coverShape();
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return coverShape();
    }

    @Override
    public boolean canBeReplaced(BlockState state, Fluid fluid) {
        return false;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ManholeBlockEntity(pos, state);
    }

    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
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
            if (stack.hasCustomHoverName()) {
                be.setName(Names.toRaw(stack.getHoverName()));
            }
            if (home && r != null && placer instanceof ServerPlayer sp) {
                be.setOwner(sp.getUUID(), sp.getGameProfile().getName(), false);
            }
        }
    }

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
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!swapping && !state.is(newState.getBlock()) && level instanceof ServerLevel sl
                && level.getBlockEntity(pos) instanceof ManholeBlockEntity be && be.nodeId() != null) {
            ManholeData.get(sl.getServer()).removeNode(be.nodeId());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.isEmpty()) {
            if (level.isClientSide) {
                return PryTools.isPryTool(stack) ? InteractionResult.CONSUME : InteractionResult.SUCCESS;
            }
            return ManholeInteraction.useItem((ServerPlayer) player, (ServerLevel) level, pos, state, hand, stack);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        return ManholeInteraction.useEmpty((ServerPlayer) player, (ServerLevel) level, pos, state);
    }
}
