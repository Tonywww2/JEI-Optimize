package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ThermalStirlingRecipeCompactor {
    private static final String RECIPE_CLASS = "cofh.thermal.core.util.recipes.dynamo.StirlingFuel";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private ThermalStirlingRecipeCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        if (!JeiOptFeatureFlags.compactThermalStirlingFuels() || recipes == null || recipes.size() < 2) {
            return recipes;
        }

        Object first = recipes.get(0);
        if (first == null || !RECIPE_CLASS.equals(first.getClass().getName())) {
            return recipes;
        }

        try {
            Class<?> recipeClass = first.getClass();
            Method inputItemsMethod = recipeClass.getMethod("getInputItems");
            Method inputFluidsMethod = recipeClass.getMethod("getInputFluids");
            Method energyMethod = recipeClass.getMethod("getEnergy");
            Method idMethod = recipeClass.getMethod("getId");
            Constructor<?> constructor = recipeClass.getConstructor(
                idMethod.getReturnType(),
                int.class,
                List.class,
                List.class
            );
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            Map<GroupKey, Group> groups = new LinkedHashMap<>();

            for (Object recipe : recipes) {
                if (!recipeClass.isInstance(recipe)) {
                    return recipes;
                }
                int energy = ((Number) energyMethod.invoke(recipe)).intValue();
                Object fluidsValue = inputFluidsMethod.invoke(recipe);
                if (!(fluidsValue instanceof List<?> fluids)) {
                    return recipes;
                }
                GroupKey key = new GroupKey(energy, List.copyOf(fluids));
                Group group = groups.computeIfAbsent(key, ignored -> new Group(recipe, energy, List.copyOf(fluids)));
                Object inputsValue = inputItemsMethod.invoke(recipe);
                if (!(inputsValue instanceof List<?> inputs)) {
                    return recipes;
                }
                for (Object input : inputs) {
                    if (!(input instanceof Ingredient ingredient)) {
                        return recipes;
                    }
                    for (ItemStack stack : ingredient.getItems()) {
                        if (!stack.isEmpty()) {
                            group.inputs().putIfAbsent(helper.getUniqueId(stack, UidContext.Recipe), stack);
                        }
                    }
                }
            }

            List<Object> compacted = new ArrayList<>(groups.size());
            for (Group group : groups.values()) {
                if (group.inputs().isEmpty()) {
                    return recipes;
                }
                Ingredient merged = Ingredient.of(group.inputs().values().stream());
                compacted.add(constructor.newInstance(
                    idMethod.invoke(group.firstRecipe()),
                    group.energy(),
                    List.of(merged),
                    group.inputFluids()
                ));
            }

            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Thermal Stirling fuels from {} to {} recipe pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Thermal Stirling Dynamo fuels; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    private record GroupKey(int energy, List<?> inputFluids) {
    }

    private record Group(
        Object firstRecipe,
        int energy,
        List<?> inputFluids,
        Map<String, ItemStack> inputs
    ) {
        private Group(Object firstRecipe, int energy, List<?> inputFluids) {
            this(firstRecipe, energy, inputFluids, new LinkedHashMap<>());
        }
    }
}