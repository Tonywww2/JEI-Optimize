package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.ItemStackRepresentativeSelector;
import com.tonywww.jeioptimize.recipe.RepresentativeExampleBudget;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmbersDawnstoneAnvilCompactor {
    private static final String RECIPE_CLASS = "com.rekindled.embers.recipe.AnvilDisplayRecipe";
    private static final Map<Object, Layout> LAYOUTS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private EmbersDawnstoneAnvilCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        boolean compact = JeiOptFeatureFlags.compactEmbersDawnstoneAnvil();
        boolean aggressive = JeiOptFeatureFlags.aggressiveEmbersDawnstoneAnvil();
        if ((!compact && !aggressive) || recipes == null || recipes.isEmpty()) {
            return recipes;
        }
        Object first = recipes.get(0);
        if (first == null || !RECIPE_CLASS.equals(first.getClass().getName())) {
            return recipes;
        }

        try {
            Class<?> recipeClass = first.getClass();
            Field idField = recipeClass.getField("id");
            Field outputsField = recipeClass.getField("outputs");
            Field inputsField = recipeClass.getField("inputs");
            Field ingredientField = recipeClass.getField("ingredient");
            Constructor<?> constructor = recipeClass.getConstructor(
                ResourceLocation.class,
                List.class,
                List.class,
                Ingredient.class
            );
            IIngredientHelper<ItemStack> helper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            Map<GroupKey, Group> groups = new LinkedHashMap<>();
            for (Object recipe : recipes) {
                if (!recipeClass.isInstance(recipe)) {
                    return recipes;
                }
                Object idValue = idField.get(recipe);
                Object ingredientValue = ingredientField.get(recipe);
                if (!(idValue instanceof ResourceLocation id) || !(ingredientValue instanceof Ingredient ingredient)) {
                    return recipes;
                }
                List<ItemStack> inputs = itemStacks(inputsField.get(recipe));
                List<ItemStack> outputs = itemStacks(outputsField.get(recipe));
                if (inputs.isEmpty() || inputs.size() != outputs.size()) {
                    return recipes;
                }
                List<ItemStack> topInputs = Arrays.asList(ingredient.getItems());
                List<String> topUids = topInputs.stream()
                    .map(stack -> helper.getUniqueId(stack, UidContext.Recipe))
                    .distinct()
                    .sorted()
                    .toList();
                Group group = groups.computeIfAbsent(
                    new GroupKey(id, topUids),
                    ignored -> new Group(recipe, id, ingredient, List.copyOf(topInputs))
                );
                group.inputs().addAll(inputs);
                group.outputs().addAll(outputs);
                group.recipes().add(recipe);
            }

            if (!aggressive && groups.size() == recipes.size()) {
                return recipes;
            }
            List<Object> compacted = new ArrayList<>(groups.size());
            RepresentativeExampleBudget<ResourceLocation> genericRepairExamples =
                new RepresentativeExampleBudget<>(JeiOptFeatureFlags.aggressiveGenericRepairRepresentatives());
            for (Group group : groups.values()) {
                List<ItemStack> inputs = List.copyOf(group.inputs());
                List<ItemStack> outputs = List.copyOf(group.outputs());
                if (aggressive) {
                    if (isGenericRepair(group.id())) {
                        int selectedCount = genericRepairExamples.take(group.id(), inputs.size());
                        if (selectedCount == 0) {
                            continue;
                        }
                        ItemStackRepresentativeSelector.ParallelSelection selection =
                            ItemStackRepresentativeSelector.selectParallelExamples(
                                inputs,
                                outputs,
                                selectedCount
                            );
                        inputs = selection.inputs();
                        outputs = selection.outputs();
                    } else {
                        ItemStackRepresentativeSelector.ParallelSelection selection =
                            ItemStackRepresentativeSelector.selectParallelFamilies(
                                inputs,
                                outputs,
                                JeiOptFeatureFlags.aggressiveRepresentativesPerGroup()
                            );
                        inputs = selection.inputs();
                        outputs = selection.outputs();
                    }
                }
                if (group.recipes().size() == 1 && inputs.size() == group.inputs().size()) {
                    compacted.add(group.firstRecipe());
                    continue;
                }
                Object recipe = constructor.newInstance(group.id(), outputs, inputs, group.ingredient());
                LAYOUTS.put(recipe, new Layout(group.topInputs(), inputs, outputs));
                compacted.add(recipe);
            }
            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Embers Dawnstone Anvil recipes from {} to {} pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Embers Dawnstone Anvil recipes; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    private static boolean isGenericRepair(ResourceLocation id) {
        String path = id.getPath();
        return path.endsWith("tool_repair") || path.endsWith("tool_materia_repair");
    }

    public static boolean addCompactedLayout(IRecipeLayoutBuilder builder, Object recipe) {
        Layout layout = LAYOUTS.get(recipe);
        if (layout == null) {
            return false;
        }
        builder.addSlot(RecipeIngredientRole.INPUT, 22, 19).addItemStacks(layout.topInputs());
        IRecipeSlotBuilder input = builder.addSlot(RecipeIngredientRole.INPUT, 22, 37)
            .addItemStacks(layout.bottomInputs());
        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, 77, 28)
            .addItemStacks(layout.outputs());
        builder.createFocusLink(input, output);
        return true;
    }

    private static List<ItemStack> itemStacks(Object value) throws ReflectiveOperationException {
        if (!(value instanceof List<?> values)) {
            throw new ReflectiveOperationException("Embers AnvilDisplayRecipe field is not a List");
        }
        List<ItemStack> stacks = new ArrayList<>(values.size());
        for (Object element : values) {
            if (!(element instanceof ItemStack stack) || stack.isEmpty()) {
                throw new ReflectiveOperationException("Embers AnvilDisplayRecipe contains an invalid ItemStack");
            }
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    private record GroupKey(ResourceLocation id, List<String> topUids) {
    }

    private record Group(
        Object firstRecipe,
        ResourceLocation id,
        Ingredient ingredient,
        List<ItemStack> topInputs,
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        List<Object> recipes
    ) {
        private Group(Object firstRecipe, ResourceLocation id, Ingredient ingredient, List<ItemStack> topInputs) {
            this(firstRecipe, id, ingredient, topInputs, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private record Layout(List<ItemStack> topInputs, List<ItemStack> bottomInputs, List<ItemStack> outputs) {
    }
}