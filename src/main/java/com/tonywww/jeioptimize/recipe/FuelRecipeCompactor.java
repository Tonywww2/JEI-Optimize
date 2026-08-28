package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.vanilla.IJeiFuelingRecipe;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.plugins.vanilla.cooking.fuel.FuelingRecipe;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FuelRecipeCompactor {
    private FuelRecipeCompactor() {
    }

    public static List<IJeiFuelingRecipe> compact(
        List<IJeiFuelingRecipe> recipes,
        IIngredientManager ingredientManager
    ) {
        if (!JeiOptFeatureFlags.compactFuelRecipes() || recipes == null || recipes.size() < 2) {
            return recipes;
        }

        try {
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            Map<Integer, Map<String, ItemStack>> inputsByBurnTime = new LinkedHashMap<>();
            int originalInputCount = 0;

            for (IJeiFuelingRecipe recipe : recipes) {
                if (recipe == null || recipe.getBurnTime() <= 0 || recipe.getInputs() == null) {
                    return recipes;
                }
                Map<String, ItemStack> inputs = inputsByBurnTime.computeIfAbsent(
                    recipe.getBurnTime(),
                    ignored -> new LinkedHashMap<>()
                );
                for (ItemStack stack : recipe.getInputs()) {
                    if (stack == null || stack.isEmpty()) {
                        return recipes;
                    }
                    originalInputCount++;
                    String uid = helper.getUniqueId(stack, UidContext.Recipe);
                    inputs.putIfAbsent(uid, stack);
                }
            }

            List<IJeiFuelingRecipe> compacted = new ArrayList<>(inputsByBurnTime.size());
            int compactedInputCount = 0;
            for (Map.Entry<Integer, Map<String, ItemStack>> entry : inputsByBurnTime.entrySet()) {
                List<ItemStack> inputs = List.copyOf(entry.getValue().values());
                compactedInputCount += inputs.size();
                compacted.add(new FuelingRecipe(inputs, entry.getKey()));
            }

            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted fuel recipes from {} pages / {} inputs to {} pages / {} unique inputs",
                recipes.size(),
                originalInputCount,
                compacted.size(),
                compactedInputCount
            );
            return List.copyOf(compacted);
        } catch (RuntimeException | LinkageError error) {
            JeiOptimize.LOGGER.warn("Could not compact JEI fuel recipes; keeping the original recipes", error);
            return recipes;
        }
    }
}