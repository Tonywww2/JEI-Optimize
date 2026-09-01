package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.recipe.CoveragePairPlanner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class IronsSpellsRecipeCompactor {
    private static final String RECIPE_CLASS = "io.redspace.ironsspellbooks.jei.ArcaneAnvilJeiRecipe";
    private static final String SPELL_REGISTRY_CLASS = "io.redspace.ironsspellbooks.api.registry.SpellRegistry";
    private static final String SPELL_CONTAINER_CLASS = "io.redspace.ironsspellbooks.api.spells.ISpellContainer";
    private static final String ITEM_REGISTRY_CLASS = "io.redspace.ironsspellbooks.registries.ItemRegistry";

    private static final Map<Object, RecipePlan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final AtomicBoolean WARNING_LOGGED = new AtomicBoolean();

    private IronsSpellsRecipeCompactor() {
    }

    public static void clear() {
        PLANS.clear();
        WARNING_LOGGED.set(false);
    }

    public static List<?> compact(List<?> recipes) {
        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }

        try {
            ClassLoader classLoader = recipes.get(0).getClass().getClassLoader();
            Class<?> recipeClass = Class.forName(RECIPE_CLASS, false, classLoader);
            Field leftItemField = declaredField(recipeClass, "leftItem");
            Field rightItemField = declaredField(recipeClass, "rightItem");

            List<Object> imbueRecipes = new ArrayList<>();
            for (Object recipe : recipes) {
                if (recipeClass.isInstance(recipe)
                    && leftItemField.get(recipe) instanceof Item
                    && rightItemField.get(recipe) == null) {
                    imbueRecipes.add(recipe);
                }
            }
            if (imbueRecipes.isEmpty()) {
                return recipes;
            }

            List<SpellVariant> variants = spellVariants(classLoader);
            List<CoveragePairPlanner.Pair> plan = CoveragePairPlanner.plan(imbueRecipes.size(), variants.size());
            if (plan.isEmpty()) {
                return recipes;
            }

            List<List<SpellVariant>> variantsByRecipe = new ArrayList<>(imbueRecipes.size());
            for (int index = 0; index < imbueRecipes.size(); index++) {
                variantsByRecipe.add(new ArrayList<>());
            }
            for (CoveragePairPlanner.Pair pair : plan) {
                variantsByRecipe.get(pair.itemIndex()).add(variants.get(pair.variantIndex()));
            }
            for (int index = 0; index < imbueRecipes.size(); index++) {
                Object recipe = imbueRecipes.get(index);
                Object item = leftItemField.get(recipe);
                PLANS.put(recipe, new RecipePlan(item, List.copyOf(variantsByRecipe.get(index))));
            }

            JeiOptimize.LOGGER.debug(
                "JEI Optimize compacted Iron's Spells imbuing from {} combinations to {} representative combinations across {} recipes",
                (long) imbueRecipes.size() * variants.size(),
                plan.size(),
                imbueRecipes.size()
            );
            return recipes;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warnOnce("Could not compact Iron's Spells Arcane Anvil recipes; keeping its original recipes", error);
            return recipes;
        }
    }

    public static Object createRecipeItems(Object recipe) {
        RecipePlan plan = PLANS.get(recipe);
        if (plan == null || !(plan.item() instanceof Item item) || plan.variants().isEmpty()) {
            return null;
        }

        try {
            ClassLoader classLoader = recipe.getClass().getClassLoader();
            Item scrollItem = findScrollItem(classLoader);
            ItemStack leftStack = new ItemStack(item);
            List<ItemStack> scrollStacks = new ArrayList<>(plan.variants().size());
            List<ItemStack> resultStacks = new ArrayList<>(plan.variants().size());
            for (SpellVariant variant : plan.variants()) {
                ItemStack scrollStack = new ItemStack(scrollItem);
                ItemStack resultStack = new ItemStack(item);
                applySpell(classLoader, variant.spell(), variant.level(), scrollStack);
                applySpell(classLoader, variant.spell(), variant.level(), resultStack);
                scrollStacks.add(scrollStack);
                resultStacks.add(resultStack);
            }

            Class<?> tupleClass = Class.forName(RECIPE_CLASS + "$Tuple", false, classLoader);
            var tupleConstructor = findConstructor(tupleClass, 3);
            return tupleConstructor.newInstance(
                List.of(leftStack),
                List.copyOf(scrollStacks),
                List.copyOf(resultStacks)
            );
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            warnOnce("Could not materialize compact Iron's Spells recipe; using its original recipe", error);
            return null;
        }
    }

    private static List<SpellVariant> spellVariants(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> registryClass = Class.forName(SPELL_REGISTRY_CLASS, false, classLoader);
        Object enabledSpellsValue = registryClass.getMethod("getEnabledSpells").invoke(null);
        if (!(enabledSpellsValue instanceof List<?> enabledSpells)) {
            return List.of();
        }

        List<Object> sortedSpells = new ArrayList<>(enabledSpells);
        sortedSpells.sort(Comparator.comparing(IronsSpellsRecipeCompactor::spellId));
        List<SpellVariant> variants = new ArrayList<>();
        for (Object spell : sortedSpells) {
            Method getMinLevel = spell.getClass().getMethod("getMinLevel");
            Method getMaxLevel = spell.getClass().getMethod("getMaxLevel");
            int minimum = (int) getMinLevel.invoke(spell);
            int maximum = (int) getMaxLevel.invoke(spell);
            for (int level = minimum; level <= maximum; level++) {
                variants.add(new SpellVariant(spell, level));
            }
        }
        return List.copyOf(variants);
    }

    private static String spellId(Object spell) {
        try {
            return String.valueOf(spell.getClass().getMethod("getSpellId").invoke(spell));
        } catch (ReflectiveOperationException | RuntimeException error) {
            return spell.getClass().getName();
        }
    }

    private static Item findScrollItem(ClassLoader classLoader) throws ReflectiveOperationException {
        Class<?> itemRegistryClass = Class.forName(ITEM_REGISTRY_CLASS, false, classLoader);
        Object holder = itemRegistryClass.getField("SCROLL").get(null);
        Object value = holder instanceof Supplier<?> supplier
            ? supplier.get()
            : holder.getClass().getMethod("get").invoke(holder);
        if (value instanceof Item item) {
            return item;
        }
        throw new ReflectiveOperationException("Iron's Spells scroll registry entry is not an Item");
    }

    private static void applySpell(
        ClassLoader classLoader,
        Object spell,
        int level,
        ItemStack stack
    ) throws ReflectiveOperationException {
        Class<?> containerClass = Class.forName(SPELL_CONTAINER_CLASS, false, classLoader);
        for (Method method : containerClass.getMethods()) {
            if (method.getName().equals("createScrollContainer")
                && Modifier.isStatic(method.getModifiers())
                && method.getParameterCount() == 3) {
                method.invoke(null, spell, level, stack);
                return;
            }
        }
        throw new NoSuchMethodException(SPELL_CONTAINER_CLASS + ".createScrollContainer");
    }

    private static Field declaredField(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static java.lang.reflect.Constructor<?> findConstructor(Class<?> type, int parameterCount)
        throws NoSuchMethodException {
        for (java.lang.reflect.Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == parameterCount) {
                constructor.setAccessible(true);
                return constructor;
            }
        }
        throw new NoSuchMethodException(type.getName() + " constructor with " + parameterCount + " parameters");
    }

    private static void warnOnce(String message, Throwable error) {
        if (WARNING_LOGGED.compareAndSet(false, true)) {
            JeiOptimize.LOGGER.warn(message, error);
        }
    }

    private record RecipePlan(Object item, List<SpellVariant> variants) {
    }

    private record SpellVariant(Object spell, int level) {
    }
}