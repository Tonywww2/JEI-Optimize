package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
//? if forge {
import net.minecraft.world.item.crafting.Recipe;
//?}

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class GeneratorGaloreRecipeCompactor {
    private static final String RECIPE_CLASS = "cy.jdkdigital.generatorgalore.common.recipe.SolidFuelRecipe";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private GeneratorGaloreRecipeCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        if (!JeiOptFeatureFlags.compactGeneratorGaloreFuels() || recipes == null || recipes.size() < 2) {
            return recipes;
        }

        Object first = recipes.get(0);
        if (first == null || !RECIPE_CLASS.equals(first.getClass().getName())) {
            return recipes;
        }

        try {
            Class<?> recipeClass = first.getClass();
            Method fuelsMethod = recipeClass.getMethod("fuels");
            Method generatorMethod = recipeClass.getMethod("generator");
            Method rateMethod = recipeClass.getMethod("rate");
            Method durationMethod = findMethod(recipeClass, "burnTime", "consumptionRate");
            Method idMethod = findOptionalMethod(recipeClass, "id");
            Constructor<?> constructor = findRecordConstructor(recipeClass);
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);

            Map<GroupKey, Group> groups = new LinkedHashMap<>();
            for (Object recipe : recipes) {
                if (!recipeClass.isInstance(recipe)) {
                    return recipes;
                }
                Object generator = generatorMethod.invoke(recipe);
                Number rate = (Number) rateMethod.invoke(recipe);
                Number duration = (Number) durationMethod.invoke(recipe);
                GroupKey key = new GroupKey(
                    ingredientKey(generator, helper),
                    Double.doubleToLongBits(rate.doubleValue()),
                    Double.doubleToLongBits(duration.doubleValue())
                );
                Group group = groups.computeIfAbsent(key, ignored -> new Group(recipe, generator, rate, duration));
                Object fuelsValue = fuelsMethod.invoke(recipe);
                if (!(fuelsValue instanceof List<?> fuels)) {
                    return recipes;
                }
                for (Object fuel : fuels) {
                    if (!(fuel instanceof Ingredient ingredient)) {
                        return recipes;
                    }
                    for (ItemStack stack : ingredient.getItems()) {
                        if (!stack.isEmpty()) {
                            group.fuels().putIfAbsent(helper.getUniqueId(stack, UidContext.Recipe), stack);
                        }
                    }
                }
            }

            List<Object> compacted = new ArrayList<>(groups.size());
            for (Group group : groups.values()) {
                if (group.fuels().isEmpty()) {
                    return recipes;
                }
                Ingredient mergedFuel = Ingredient.of(group.fuels().values().stream());
                Object compactedRecipe;
                if (constructor.getParameterCount() == 5) {
                    compactedRecipe = constructor.newInstance(
                        recipeId(group.firstRecipe(), idMethod),
                        List.of(mergedFuel),
                        group.generator(),
                        group.rate().floatValue(),
                        group.duration().intValue()
                    );
                } else if (constructor.getParameterCount() == 4) {
                    compactedRecipe = constructor.newInstance(
                        List.of(mergedFuel),
                        group.generator(),
                        group.rate().floatValue(),
                        group.duration().floatValue()
                    );
                } else {
                    return recipes;
                }
                compacted.add(compactedRecipe);
            }

            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Generator Galore solid fuels from {} to {} recipe pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Generator Galore solid-fuel recipes; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    private static String ingredientKey(Object ingredient, IIngredientHelper<ItemStack> helper) {
        if (ingredient instanceof ItemStack stack) {
            return helper.getUniqueId(stack, UidContext.Recipe);
        }
        if (ingredient instanceof Ingredient vanillaIngredient) {
            return java.util.Arrays.stream(vanillaIngredient.getItems())
                .map(stack -> helper.getUniqueId(stack, UidContext.Recipe))
                .sorted(Comparator.naturalOrder())
                .reduce((left, right) -> left + "\u0000" + right)
                .orElse("");
        }
        return ingredient.getClass().getName() + ':' + ingredient;
    }

    private static Method findMethod(Class<?> type, String... names) throws NoSuchMethodException {
        Method method = findOptionalMethod(type, names);
        if (method != null) {
            return method;
        }
        throw new NoSuchMethodException(type.getName() + " does not declare " + String.join(" or ", names));
    }

    private static Method findOptionalMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Constructor<?> findRecordConstructor(Class<?> type) throws NoSuchMethodException {
        int componentCount = type.isRecord() ? type.getRecordComponents().length : -1;
        for (Constructor<?> constructor : type.getConstructors()) {
            if (constructor.getParameterCount() == componentCount) {
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + " canonical record constructor");
    }

    private static Object recipeId(Object recipe, Method idMethod) throws ReflectiveOperationException {
        if (idMethod != null) {
            return idMethod.invoke(recipe);
        }
        //? if forge {
        if (recipe instanceof Recipe<?> minecraftRecipe) {
            return minecraftRecipe.getId();
        }
        //?}
        throw new ReflectiveOperationException("Generator Galore fuel recipe has no accessible id");
    }

    private record GroupKey(String generator, long rate, long duration) {
    }

    private record Group(
        Object firstRecipe,
        Object generator,
        Number rate,
        Number duration,
        Map<String, ItemStack> fuels
    ) {
        private Group(Object firstRecipe, Object generator, Number rate, Number duration) {
            this(firstRecipe, generator, rate, duration, new LinkedHashMap<>());
        }
    }
}