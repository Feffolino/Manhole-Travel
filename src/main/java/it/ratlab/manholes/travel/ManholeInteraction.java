// SPDX-License-Identifier: MIT
package it.ratlab.manholes.travel;

import it.ratlab.manholes.ModRegistry;
import it.ratlab.manholes.api.ManholesAPI;
import it.ratlab.manholes.block.ManholeBlock;
import it.ratlab.manholes.block.ManholeBlockEntity;
import it.ratlab.manholes.data.ManholeData;
import it.ratlab.manholes.data.Names;
import it.ratlab.manholes.data.NodeRecord;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side right-click logic of both manhole blocks. */
public final class ManholeInteraction {
    /** Debounce: holding right-click fires a use every 4 ticks; don't reopen the screen each time. */
    private static final Map<UUID, Long> SCREEN_DEBOUNCE = new HashMap<>();

    private ManholeInteraction() {}

    public static ItemInteractionResult useItem(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state,
            InteractionHand hand, ItemStack stack) {
        NodeRecord node = ManholesAPI.nodeAt(level, pos);
        if (node == null) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean home = state.getBlock() instanceof ManholeBlock b && b.isHome();
        if (home) {
            HomeManholes.claim(player, node);
            if (!Access.canSee(player, node)) {
                privateHome(player, node);
                return ItemInteractionResult.SUCCESS;
            }
            HomeManholes.refreshOwnerName(player, node);
            if (stack.is(Items.NAME_TAG) && stack.has(DataComponents.CUSTOM_NAME)
                    && level.getBlockEntity(pos) instanceof ManholeBlockEntity be) {
                // The base name is the owner's (or an op's) business; team mates use the travel-screen alias.
                if (!Access.canManage(player, node)) {
                    player.displayClientMessage(Component.translatable("manholes.message.home_not_yours",
                            HomeManholes.ownerLabel(node.ownerName)), true);
                    return ItemInteractionResult.SUCCESS;
                }
                Component newName = stack.get(DataComponents.CUSTOM_NAME);
                be.setName(Names.toRaw(newName, level.registryAccess()));
                stack.consume(1, player);
                player.displayClientMessage(Component.translatable("manholes.message.renamed", newName), true);
                return ItemInteractionResult.SUCCESS;
            }
            openScreen(player, node);
            return ItemInteractionResult.SUCCESS;
        }
        if (Access.canSee(player, node)) {
            openScreen(player, node);
            return ItemInteractionResult.SUCCESS;
        }
        if (it.ratlab.manholes.item.PryTools.isPryTool(stack)) {
            PryHandler.pry(player, level, pos, node, hand, stack);
            return ItemInteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("manholes.message.rusted_shut"), true);
        return ItemInteractionResult.SUCCESS;
    }

    public static InteractionResult useEmpty(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        NodeRecord node = ManholesAPI.nodeAt(level, pos);
        if (node == null) {
            return InteractionResult.PASS;
        }
        if (node.home) {
            HomeManholes.claim(player, node);
            if (!Access.canSee(player, node)) {
                privateHome(player, node);
                return InteractionResult.SUCCESS;
            }
            HomeManholes.refreshOwnerName(player, node);
            if (player.isSecondaryUseActive()) {
                // Sneak + right-click: the owner toggles "share with team".
                HomeManholes.setShared(player.server, player, node, !node.shared);
                return InteractionResult.SUCCESS;
            }
            openScreen(player, node);
            return InteractionResult.SUCCESS;
        }
        if (Access.canSee(player, node)) {
            openScreen(player, node);
        } else {
            player.displayClientMessage(Component.translatable("manholes.message.rusted_shut"), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Someone else's private home: nothing to see, just whose it is. */
    private static void privateHome(ServerPlayer player, NodeRecord node) {
        player.displayClientMessage(Component.translatable("manholes.message.home_private_other",
                HomeManholes.ownerLabel(node.ownerName)), true);
    }

    static void debounce(ServerPlayer player, int ticks) {
        SCREEN_DEBOUNCE.put(player.getUUID(), player.serverLevel().getGameTime() + ticks);
    }

    static void forget(UUID player) {
        SCREEN_DEBOUNCE.remove(player);
    }

    private static void openScreen(ServerPlayer player, NodeRecord node) {
        long now = player.serverLevel().getGameTime();
        Long until = SCREEN_DEBOUNCE.get(player.getUUID());
        if (until != null && now < until) {
            return;
        }
        SCREEN_DEBOUNCE.put(player.getUUID(), now + 10);
        TravelHandler.openScreen(player, node);
    }
}
