// SPDX-License-Identifier: MIT
package it.ratlab.manholes.recipe;

import com.google.gson.JsonObject;
import it.ratlab.manholes.Manholes;
import it.ratlab.manholes.ManholesConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/**
 * Forge load condition {@code {"type": "manholes:default_recipes_enabled"}}: true while
 * {@code recipes.enableDefaultRecipes} is on.
 */
public final class DefaultRecipesCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation(Manholes.MOD_ID, "default_recipes_enabled");
    public static final DefaultRecipesCondition INSTANCE = new DefaultRecipesCondition();

    private DefaultRecipesCondition() {}

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return ManholesConfig.b(ManholesConfig.ENABLE_DEFAULT_RECIPES);
    }

    @Override
    public String toString() {
        return "manholes:default_recipes_enabled";
    }

    public static final class Serializer implements IConditionSerializer<DefaultRecipesCondition> {
        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {}

        @Override
        public void write(JsonObject json, DefaultRecipesCondition value) {}

        @Override
        public DefaultRecipesCondition read(JsonObject json) {
            return DefaultRecipesCondition.INSTANCE;
        }

        @Override
        public ResourceLocation getID() {
            return ID;
        }
    }
}
