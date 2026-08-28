package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MekanismNutritionalRecipeCompactor {
    private static final String RECIPE_CLASS = "mekanism.common.recipe.impl.NutritionalLiquifierIRecipe";
    private static final String CREATOR_ACCESS_CLASS = "mekanism.api.recipes.ingredients.creator.IngredientCreatorAccess";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private MekanismNutritionalRecipeCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        if (!JeiOptFeatureFlags.compactMekanismNutritionalLiquifier()
            || recipes == null
            || recipes.size() < 2) {
            return recipes;
        }

        Object first = recipes.get(0);
        if (first == null || !RECIPE_CLASS.equals(first.getClass().getName())) {
            return recipes;
        }

        try {
            Class<?> recipeClass = first.getClass();
            Method inputMethod = recipeClass.getMethod("getInput");
            Method outputsMethod = recipeClass.getMethod("getOutputDefinition");
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            Map<OutputKey, Group> groups = new LinkedHashMap<>();

            for (Object recipe : recipes) {
                if (!recipeClass.isInstance(recipe)) {
                    return recipes;
                }
                Object input = inputMethod.invoke(recipe);
                Object outputValue = outputsMethod.invoke(recipe);
                if (!(outputValue instanceof List<?> outputs) || outputs.size() != 1) {
                    return recipes;
                }
                Object output = outputs.get(0);
                Group group = groups.computeIfAbsent(outputKey(output), ignored -> new Group(recipe, output));
                Object representationsValue = input.getClass().getMethod("getRepresentations").invoke(input);
                if (!(representationsValue instanceof List<?> representations)) {
                    return recipes;
                }
                for (Object representation : representations) {
                    if (!(representation instanceof ItemStack stack) || stack.isEmpty()) {
                        return recipes;
                    }
                    group.inputs().putIfAbsent(helper.getUniqueId(stack, UidContext.Recipe), stack);
                }
            }

            Object creator = Class.forName(CREATOR_ACCESS_CLASS, false, recipeClass.getClassLoader())
                .getMethod("item")
                .invoke(null);
            Method fromIngredient = findFromIngredient(creator.getClass());
            Constructor<?> constructor = findConstructor(recipeClass);
            List<Object> compacted = new ArrayList<>(groups.size());
            for (Group group : groups.values()) {
                if (group.inputs().isEmpty()) {
                    return recipes;
                }
                Ingredient merged = Ingredient.of(group.inputs().values().stream());
                Object mekanismIngredient = fromIngredient.getParameterCount() == 1
                    ? fromIngredient.invoke(creator, merged)
                    : fromIngredient.invoke(creator, merged, 1);
                Item item = group.inputs().values().iterator().next().getItem();
                compacted.add(constructor.newInstance(item, mekanismIngredient, group.output()));
            }

            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Mekanism Nutritional Liquifier recipes from {} to {} pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Mekanism Nutritional Liquifier recipes; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    private static OutputKey outputKey(Object output) throws ReflectiveOperationException {
        Object fluid = invokeOptional(output, "getFluid");
        Object amount = invokeOptional(output, "getAmount");
        Object metadata = invokeOptional(output, "getTag");
        if (metadata == null) {
            metadata = invokeOptional(output, "getComponentsPatch");
        }
        return new OutputKey(output.getClass(), fluid, amount, metadata);
    }

    private static Object invokeOptional(Object target, String methodName) throws ReflectiveOperationException {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findFromIngredient(Class<?> creatorClass) throws NoSuchMethodException {
        Method fallback = null;
        for (Method method : creatorClass.getMethods()) {
            if (!"from".equals(method.getName()) || method.getParameterCount() < 1) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            if (Ingredient.class.isAssignableFrom(parameters[0])) {
                if (parameters.length == 1) {
                    return method;
                }
                if (parameters.length == 2 && parameters[1] == int.class) {
                    fallback = method;
                }
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new NoSuchMethodException(creatorClass.getName() + ".from(Ingredient[, int])");
    }

    private static Constructor<?> findConstructor(Class<?> recipeClass) throws NoSuchMethodException {
        for (Constructor<?> constructor : recipeClass.getConstructors()) {
            Class<?>[] parameters = constructor.getParameterTypes();
            if (parameters.length == 3 && Item.class.isAssignableFrom(parameters[0])) {
                return constructor;
            }
        }
        throw new NoSuchMethodException(recipeClass.getName() + " Item constructor");
    }

    private record OutputKey(Class<?> type, Object fluid, Object amount, Object metadata) {
        private OutputKey {
            Objects.requireNonNull(type);
        }
    }

    private record Group(Object firstRecipe, Object output, Map<String, ItemStack> inputs) {
        private Group(Object firstRecipe, Object output) {
            this(firstRecipe, output, new LinkedHashMap<>());
        }
    }
}