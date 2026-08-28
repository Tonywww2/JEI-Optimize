package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TinkersCastingRecipeCompactor {
    private static final String DISPLAY_RECIPE_CLASS =
        "slimeknights.tconstruct.library.recipe.casting.DisplayCastingRecipe";
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private TinkersCastingRecipeCompactor() {
    }

    public static List<?> compact(List<?> recipes, IIngredientManager ingredientManager) {
        if (!JeiOptFeatureFlags.compactTinkersCasting() || recipes == null || recipes.size() < 2) {
            return recipes;
        }

        try {
            ClassLoader classLoader = TinkersCastingRecipeCompactor.class.getClassLoader();
            Class<?> displayRecipeClass = Class.forName(DISPLAY_RECIPE_CLASS, false, classLoader);
            Method recipeIdMethod = displayRecipeClass.getMethod("getRecipeId");
            Method typeMethod = displayRecipeClass.getMethod("getType");
            Method castItemsMethod = displayRecipeClass.getMethod("getCastItems");
            Method fluidsMethod = displayRecipeClass.getMethod("getFluids");
            Method outputsMethod = displayRecipeClass.getMethod("getOutputs");
            Method coolingTimeMethod = displayRecipeClass.getMethod("getCoolingTime");
            Method consumedMethod = displayRecipeClass.getMethod("isConsumed");
            Constructor<?> constructor = displayRecipeClass.getConstructor(
                ResourceLocation.class,
                RecipeType.class,
                List.class,
                List.class,
                List.class,
                int.class,
                boolean.class
            );
            IIngredientHelper<Object> fluidHelper = fluidHelper(ingredientManager, classLoader);
            Class<?> fluidStackClass = Class.forName("net.minecraftforge.fluids.FluidStack", false, classLoader);
            Method fluidAmountMethod = fluidStackClass.getMethod("getAmount");
            Method fluidTagMethod = fluidStackClass.getMethod("getTag");
            IIngredientHelper<ItemStack> itemHelper = ingredientManager.getIngredientHelper(
                mezz.jei.api.constants.VanillaTypes.ITEM_STACK
            );

            Map<GroupKey, Group> groups = new LinkedHashMap<>();
            List<Entry> entries = new ArrayList<>(recipes.size());
            for (Object recipe : recipes) {
                if (!displayRecipeClass.isInstance(recipe)) {
                    entries.add(new Entry(recipe, null));
                    continue;
                }
                Object idValue = recipeIdMethod.invoke(recipe);
                Object typeValue = typeMethod.invoke(recipe);
                if (!(idValue instanceof ResourceLocation id) || !(typeValue instanceof RecipeType<?> type)) {
                    entries.add(new Entry(recipe, null));
                    continue;
                }
                List<ItemStack> casts = itemStacks(castItemsMethod.invoke(recipe));
                List<ItemStack> outputs = itemStacks(outputsMethod.invoke(recipe));
                List<Object> fluids = objects(fluidsMethod.invoke(recipe));
                List<Pair> pairs = pairs(casts, outputs, itemHelper);
                if (pairs == null || pairs.isEmpty() || fluids.isEmpty()) {
                    entries.add(new Entry(recipe, null));
                    continue;
                }
                int coolingTime = ((Number) coolingTimeMethod.invoke(recipe)).intValue();
                boolean consumed = (Boolean) consumedMethod.invoke(recipe);
                List<FluidKey> fluidKeys = new ArrayList<>(fluids.size());
                for (Object fluid : fluids) {
                    if (!fluidStackClass.isInstance(fluid)) {
                        throw new ReflectiveOperationException("Tinkers display recipe contains an invalid FluidStack");
                    }
                    fluidKeys.add(new FluidKey(
                        fluidHelper.getUniqueId(fluid, UidContext.Recipe),
                        ((Number) fluidAmountMethod.invoke(fluid)).intValue(),
                        String.valueOf(fluidTagMethod.invoke(fluid))
                    ));
                }
                GroupKey key = new GroupKey(id, type, List.copyOf(fluidKeys), coolingTime, consumed);
                Group group = groups.computeIfAbsent(
                    key,
                    ignored -> new Group(recipe, id, type, List.copyOf(fluids), coolingTime, consumed)
                );
                for (Pair pair : pairs) {
                    group.pairs().putIfAbsent(pair.uid(), pair);
                }
                group.count++;
                entries.add(new Entry(recipe, key));
            }

            boolean changed = groups.values().stream().anyMatch(group -> group.count > 1);
            if (!changed) {
                return recipes;
            }
            List<Object> compacted = new ArrayList<>(recipes.size());
            Map<GroupKey, Boolean> emitted = new LinkedHashMap<>();
            for (Entry entry : entries) {
                if (entry.key() == null) {
                    compacted.add(entry.recipe());
                    continue;
                }
                Group group = groups.get(entry.key());
                if (group.count == 1) {
                    compacted.add(group.firstRecipe());
                } else if (emitted.putIfAbsent(entry.key(), Boolean.TRUE) == null) {
                    List<ItemStack> casts = group.pairs().values().stream().map(Pair::cast).toList();
                    List<ItemStack> outputs = group.pairs().values().stream().map(Pair::output).toList();
                    compacted.add(constructor.newInstance(
                        group.id(),
                        group.type(),
                        casts,
                        group.fluids(),
                        outputs,
                        group.coolingTime(),
                        group.consumed()
                    ));
                }
            }
            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Tinkers casting recipes from {} to {} pages",
                recipes.size(),
                compacted.size()
            );
            return List.copyOf(compacted);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            if (WARNING_LOGGED.compareAndSet(false, true)) {
                JeiOptimize.LOGGER.warn(
                    "Could not compact Tinkers casting recipes; keeping the original recipes",
                    error
                );
            }
            return recipes;
        }
    }

    @SuppressWarnings("unchecked")
    private static IIngredientHelper<Object> fluidHelper(
        IIngredientManager ingredientManager,
        ClassLoader classLoader
    ) throws ReflectiveOperationException {
        Class<?> forgeTypes = Class.forName("mezz.jei.api.forge.ForgeTypes", false, classLoader);
        Field field = forgeTypes.getField("FLUID_STACK");
        Object value = field.get(null);
        if (!(value instanceof IIngredientType<?> ingredientType)) {
            throw new ReflectiveOperationException("ForgeTypes.FLUID_STACK is not an ingredient type");
        }
        return ingredientManager.getIngredientHelper((IIngredientType<Object>) ingredientType);
    }

    private static List<Pair> pairs(
        List<ItemStack> casts,
        List<ItemStack> outputs,
        IIngredientHelper<ItemStack> helper
    ) {
        if (casts.isEmpty() || outputs.isEmpty()) {
            return null;
        }
        List<Pair> pairs = new ArrayList<>(casts.size());
        if (outputs.size() == casts.size()) {
            for (int index = 0; index < casts.size(); index++) {
                pairs.add(pair(casts.get(index), outputs.get(index), helper));
            }
            return pairs;
        }
        if (outputs.size() == 1) {
            ItemStack output = outputs.get(0);
            for (ItemStack cast : casts) {
                pairs.add(pair(cast, output, helper));
            }
            return pairs;
        }
        return null;
    }

    private static Pair pair(ItemStack cast, ItemStack output, IIngredientHelper<ItemStack> helper) {
        String uid = helper.getUniqueId(cast, UidContext.Recipe)
            + '\u0000'
            + helper.getUniqueId(output, UidContext.Recipe);
        return new Pair(uid, cast, output);
    }

    private static List<ItemStack> itemStacks(Object value) throws ReflectiveOperationException {
        List<Object> objects = objects(value);
        List<ItemStack> stacks = new ArrayList<>(objects.size());
        for (Object object : objects) {
            if (!(object instanceof ItemStack stack) || stack.isEmpty()) {
                throw new ReflectiveOperationException("Tinkers display recipe contains an invalid ItemStack");
            }
            stacks.add(stack);
        }
        return List.copyOf(stacks);
    }

    private static List<Object> objects(Object value) throws ReflectiveOperationException {
        if (!(value instanceof List<?> list)) {
            throw new ReflectiveOperationException("Tinkers display recipe value is not a List");
        }
        if (list.stream().anyMatch(element -> element == null)) {
            throw new ReflectiveOperationException("Tinkers display recipe contains a null value");
        }
        return List.copyOf(list);
    }

    private record GroupKey(
        ResourceLocation id,
        RecipeType<?> type,
        List<FluidKey> fluidKeys,
        int coolingTime,
        boolean consumed
    ) {
    }

    private record FluidKey(String uid, int amount, String tag) {
    }

    private static final class Group {
        private final Object firstRecipe;
        private final ResourceLocation id;
        private final RecipeType<?> type;
        private final List<Object> fluids;
        private final int coolingTime;
        private final boolean consumed;
        private final Map<String, Pair> pairs = new LinkedHashMap<>();
        private int count;

        private Group(
            Object firstRecipe,
            ResourceLocation id,
            RecipeType<?> type,
            List<Object> fluids,
            int coolingTime,
            boolean consumed
        ) {
            this.firstRecipe = firstRecipe;
            this.id = id;
            this.type = type;
            this.fluids = fluids;
            this.coolingTime = coolingTime;
            this.consumed = consumed;
        }

        private Object firstRecipe() {
            return firstRecipe;
        }

        private ResourceLocation id() {
            return id;
        }

        private RecipeType<?> type() {
            return type;
        }

        private List<Object> fluids() {
            return fluids;
        }

        private int coolingTime() {
            return coolingTime;
        }

        private boolean consumed() {
            return consumed;
        }

        private Map<String, Pair> pairs() {
            return pairs;
        }
    }

    private record Pair(String uid, ItemStack cast, ItemStack output) {
    }

    private record Entry(Object recipe, GroupKey key) {
    }
}