// SPDX-License-Identifier: MIT
package it.ratlab.manholes.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * {@code manholes:crowbar}: the default (and only) member of {@code #manholes:pry_tools}. Stack size 1, durability from
 * the startup config, repairable in an anvil with {@code #c:ingots/iron}. No recipe is shipped: the pack adds one.
 */
public class CrowbarItem extends Item {
    public static final TagKey<Item> IRON_INGOTS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ingots/iron"));

    public CrowbarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(IRON_INGOTS);
    }
}
