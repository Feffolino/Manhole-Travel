// SPDX-License-Identifier: MIT
package it.ratlab.manholes.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * {@code manholes:crowbar}: the default (and only) member of {@code #manholes:pry_tools}.
 * Stack size 1, durability from the startup config, repairable with iron ingots.
 */
public class CrowbarItem extends Item {
    public static final TagKey<Item> FORGE_IRON_INGOTS = TagKey.create(Registries.ITEM, new ResourceLocation("forge", "ingots/iron"));
    public static final TagKey<Item> C_IRON_INGOTS = TagKey.create(Registries.ITEM, new ResourceLocation("c", "ingots/iron"));

    public CrowbarItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isValidRepairItem(ItemStack stack, ItemStack repairCandidate) {
        return repairCandidate.is(FORGE_IRON_INGOTS) || repairCandidate.is(C_IRON_INGOTS) || repairCandidate.is(Items.IRON_INGOT);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
    }
}
