// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class ManholeInteraction {
    private ManholeInteraction() {}

    public static InteractionResult useItem(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
                                            InteractionHand hand, ItemStack stack) {
        return InteractionResult.SUCCESS;
    }

    public static InteractionResult useEmpty(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        return InteractionResult.SUCCESS;
    }
}
