// SPDX-License-Identifier: MIT
package it.ratlab.manholes.recipe;

import com.mojang.serialization.MapCodec;
import it.ratlab.manholes.ManholesConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * NeoForge load condition {@code {"type": "manholes:default_recipes_enabled"}}: true while
 * {@code recipes.enableDefaultRecipes} is on. Used by the mod's own recipes; evaluated when datapacks load.
 */
public final class DefaultRecipesCondition implements ICondition {
    public static final DefaultRecipesCondition INSTANCE = new DefaultRecipesCondition();
    public static final MapCodec<DefaultRecipesCondition> CODEC = MapCodec.unit(INSTANCE);

    private DefaultRecipesCondition() {}

    @Override
    public boolean test(IContext context) {
        return ManholesConfig.b(ManholesConfig.ENABLE_DEFAULT_RECIPES);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "manholes:default_recipes_enabled";
    }
}
