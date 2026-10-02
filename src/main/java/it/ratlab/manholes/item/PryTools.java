// SPDX-License-Identifier: MIT
package it.ratlab.manholes.item;

import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ModRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Decides whether an item pries manholes: a member of {@code #manholes:pry_tools}, or, with
 * {@code prying.matchAnyCrowbar = true} (default), any item whose registry path contains {@code crowbar}.
 */
public final class PryTools {
    private PryTools() {}

    public static boolean isPryTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.is(ModRegistry.PRY_TOOLS)) {
            return true;
        }
        return ManholesConfig.b(ManholesConfig.MATCH_ANY_CROWBAR) && nameLooksLikeCrowbar(stack.getItem());
    }

    public static boolean nameLooksLikeCrowbar(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && id.getPath().contains("crowbar");
    }
}
