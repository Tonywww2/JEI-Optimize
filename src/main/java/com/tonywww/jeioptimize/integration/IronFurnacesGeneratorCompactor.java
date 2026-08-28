package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class IronFurnacesGeneratorCompactor {
    private static final String RECIPE_CLASS = "ironfurnaces.recipes.SimpleGeneratorRecipe";
    private static final Map<Object, List<ItemStack>> INPUTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private IronFurnacesGeneratorCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        if (!JeiOptFeatureFlags.compactIronFurnacesGenerator() || recipes == null || recipes.size() < 2) {
            return recipes;
        }
        Object first = recipes.get(0);
        if (first == null || !RECIPE_CLASS.equals(first.getClass().getName())) {
            return recipes;
        }

        try {
            Class<?> recipeClass = first.getClass();
            Method energyMethod = recipeClass.getMethod("getEnergy");
            Method ingredientMethod = recipeClass.getMethod("getIngredient");
            Constructor<?> constructor = recipeClass.getConstructor(int.class, ItemStack.class);
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            Map<Integer, Map<String, ItemStack>> inputsByEnergy = new LinkedHashMap<>();
            for (Object recipe : recipes) {
                if (!recipeClass.isInstance(recipe)) {
                    return recipes;
                }
                int energy = ((Number) energyMethod.invoke(recipe)).intValue();
                Object ingredient = ingredientMethod.invoke(recipe);
                if (!(ingredient instanceof ItemStack stack) || stack.isEmpty()) {
                    return recipes;
                }
                inputsByEnergy.computeIfAbsent(energy, ignored -> new LinkedHashMap<>())
                    .putIfAbsent(helper.getUniqueId(stack, UidContext.Recipe), stack);
            }

            List<Object> compacted = new ArrayList<>(inputsByEnergy.size());
            for (Map.Entry<Integer, Map<String, ItemStack>> entry : inputsByEnergy.entrySet()) {
                List<ItemStack> inputs = List.copyOf(entry.getValue().values());
                Object recipe = constructor.newInstance(entry.getKey(), inputs.get(0));
                INPUTS.put(recipe, inputs);
                compacted.add(recipe);
            }
            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Iron Furnaces generator recipes from {} to {} pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Iron Furnaces generator recipes; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    public static boolean addCompactedInput(IRecipeLayoutBuilder builder, Object recipe) {
        List<ItemStack> inputs = INPUTS.get(recipe);
        if (inputs == null) {
            return false;
        }
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 18)
            .addIngredients(VanillaTypes.ITEM_STACK, inputs);
        return true;
    }
}