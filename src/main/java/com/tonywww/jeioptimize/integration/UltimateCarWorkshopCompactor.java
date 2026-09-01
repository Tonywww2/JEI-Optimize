package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UltimateCarWorkshopCompactor {
    private static final String BUILDER_CLASS = "de.maxhenkel.car.integration.jei.CarRecipeBuilder";
    private static final String RECIPE_CLASS = "de.maxhenkel.car.integration.jei.CarRecipe";
    private static final Map<Object, List<List<ItemStack>>> SLOT_INPUTS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private UltimateCarWorkshopCompactor() {
    }

    public static void clear() {
        SLOT_INPUTS.clear();
        WARNING_LOGGED.set(false);
    }

    public static List<?> createCompactRecipes(ClassLoader classLoader) {
        try {
            Class<?> builderClass = Class.forName(BUILDER_CLASS, false, classLoader);
            Class<?> recipeClass = Class.forName(RECIPE_CLASS, false, classLoader);
            Constructor<?> constructor = recipeClass.getConstructor(List.class);

            List<ItemStack> plates = items(builderClass, "getAllLicensePlateHolders");
            List<ItemStack> tanks = items(builderClass, "getAllTanks");
            List<ItemStack> engines = items(builderClass, "getAllEngines");
            List<ItemStack> bumpers = items(builderClass, "getAllBumpers");
            List<ItemStack> woodBodies = items(builderClass, "getWoodBodies");
            List<ItemStack> suvBodies = items(builderClass, "getSUVBodies");
            List<ItemStack> sportBodies = items(builderClass, "getSportBodies");
            List<ItemStack> transportBodies = items(builderClass, "getTransporters");
            List<ItemStack> containers = items(builderClass, "getAllContainers");
            List<ItemStack> tankContainers = items(builderClass, "getAllTankContainers");

            ItemStack wheel = itemField("de.maxhenkel.car.items.ModItems", "WHEEL", classLoader);
            ItemStack largeWheel = itemField("de.maxhenkel.car.items.ModItems", "BIG_WHEEL", classLoader);
            List<Object> recipes = new ArrayList<>(5);
            recipes.add(create(constructor, List.of(
                woodBodies, bumpers, plates, tanks, engines,
                List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel)
            )));
            recipes.add(create(constructor, List.of(
                suvBodies, plates, tanks, engines,
                List.of(largeWheel), List.of(largeWheel), List.of(largeWheel), List.of(largeWheel)
            )));
            recipes.add(create(constructor, List.of(
                sportBodies, plates, tanks, engines,
                List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel)
            )));
            recipes.add(create(constructor, List.of(
                transportBodies, containers, plates, tanks, engines,
                List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel)
            )));
            recipes.add(create(constructor, List.of(
                transportBodies, tankContainers, plates, tanks, engines,
                List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel), List.of(wheel)
            )));

            JeiOptimize.LOGGER.debug("JEI Optimize replaced Ultimate Car Mod's workshop combinations with 5 layout pages");
            return List.copyOf(recipes);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Ultimate Car Mod workshop recipes; using its original generator",
                    error
                );
            }
            return null;
        }
    }

    public static boolean addCompactSlots(IRecipeLayoutBuilder builder, Object recipe) {
        List<List<ItemStack>> slots = SLOT_INPUTS.get(recipe);
        if (slots == null) {
            return false;
        }
        for (int index = 0; index < slots.size(); index++) {
            List<ItemStack> inputs = slots.get(index);
            if (!inputs.isEmpty()) {
                int x = index % 5;
                int y = index / 5;
                builder.addSlot(RecipeIngredientRole.INPUT, x * 18 + 1, y * 18 + 1)
                    .addIngredients(VanillaTypes.ITEM_STACK, inputs);
            }
        }
        return true;
    }

    private static Object create(Constructor<?> constructor, List<List<ItemStack>> slots)
        throws ReflectiveOperationException {
        List<ItemStack> representative = slots.stream().map(UltimateCarWorkshopCompactor::first).toList();
        Object recipe = constructor.newInstance(representative);
        SLOT_INPUTS.put(recipe, slots.stream().map(List::copyOf).toList());
        return recipe;
    }

    private static ItemStack first(List<ItemStack> inputs) {
        if (inputs.isEmpty()) {
            throw new IllegalStateException("Ultimate Car Mod returned an empty workshop part group");
        }
        return inputs.get(0);
    }

    private static List<ItemStack> items(Class<?> builderClass, String methodName)
        throws ReflectiveOperationException {
        Object value = builderClass.getMethod(methodName).invoke(null);
        if (!(value instanceof List<?> list)) {
            throw new ReflectiveOperationException(methodName + " did not return a List");
        }
        List<ItemStack> result = new ArrayList<>(list.size());
        for (Object element : list) {
            if (!(element instanceof ItemStack stack) || stack.isEmpty()) {
                throw new ReflectiveOperationException(methodName + " returned an invalid ItemStack");
            }
            result.add(stack);
        }
        return List.copyOf(result);
    }

    private static ItemStack itemField(String className, String fieldName, ClassLoader classLoader)
        throws ReflectiveOperationException {
        Class<?> type = Class.forName(className, false, classLoader);
        Object holder = type.getField(fieldName).get(null);
        Object item = holder.getClass().getMethod("get").invoke(holder);
        if (item instanceof net.minecraft.world.level.ItemLike itemLike) {
            return new ItemStack(itemLike);
        }
        throw new ReflectiveOperationException(className + '.' + fieldName + " is not an ItemLike holder");
    }
}