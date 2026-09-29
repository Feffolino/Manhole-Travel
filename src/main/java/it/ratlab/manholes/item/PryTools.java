// SPDX-License-Identifier: MIT
package it.ratlab.manholes.item;

import it.ratlab.manholes.ManholesConfig;
import it.ratlab.manholes.ModRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The one place that decides whether an item pries manholes: a member of {@code #manholes:pry_tools}, or, with
 * {@code prying.matchAnyCrowbar = true} (default), any item whose registry path contains {@code crowbar}
 * (e.g. {@code somemod:rusty_crowbar}). Used for prying, swing suppression, the pose and the left-click suppression.
 * <p>
 * On a client connected to a server the config value is the client's own copy of the common config (not synced); it
 * only affects the client-side swing / left-click suppression, the server decides whether prying happens.
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
