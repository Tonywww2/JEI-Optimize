package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.library.plugins.vanilla.crafting.CategoryRecipeValidator;
import net.minecraft.world.item.crafting.CraftingRecipe;
//? if neoforge {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reimplements JEI's {@code VanillaRecipes.getCraftingRecipes} with a parallel validation pass.
 *
 * <p>The per-recipe work ({@code CategoryRecipeValidator.isRecipeValid}/{@code isRecipeHandled}) is
 * read-only and independent, so it can run across CPU cores. The result is a
 * {@link Collectors#partitioningBy partition} that preserves encounter order and content, so it is
 * identical to JEI's sequential output. Any failure falls back to a sequential pass.
 */
public final class VanillaRecipeParallelBuilder {
    private static final int CRAFTING_MAX_INPUTS = 9;

    private VanillaRecipeParallelBuilder() {
    }

    //? if forge {
    public static Map<Boolean, List<CraftingRecipe>> buildCraftingRecipes(
        RecipeManager recipeManager,
        IIngredientManager ingredientManager,
        IRecipeCategory<CraftingRecipe> craftingCategory
    ) {
    //?} else {
    /*public static Map<Boolean, List<RecipeHolder<CraftingRecipe>>> buildCraftingRecipes(
        RecipeManager recipeManager,
        IIngredientManager ingredientManager,
        IRecipeCategory<RecipeHolder<CraftingRecipe>> craftingCategory
    ) {
    *///?}
        CategoryRecipeValidator<CraftingRecipe> validator =
            new CategoryRecipeValidator<>(craftingCategory, ingredientManager, CRAFTING_MAX_INPUTS);
        //? if forge {
        List<CraftingRecipe> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        //?} else {
        /*List<RecipeHolder<CraftingRecipe>> allRecipes = recipeManager.getAllRecipesFor(RecipeType.CRAFTING);
        *///?}

        long start = System.nanoTime();
        //? if forge {
        Map<Boolean, List<CraftingRecipe>> partitioned;
        //?} else {
        /*Map<Boolean, List<RecipeHolder<CraftingRecipe>>> partitioned;
        *///?}
        // parallelStream() runs on the shared ForkJoinPool and blocks the main thread until every
        // task completes. During an in-world JEI rebuild (for example a recipe hot-reload) the
        // recipe/registry/tag data is being rebuilt on the main thread, so a validation task on a
        // pool thread can need the main thread (or a registry lock) and stall, and the main thread
        // then joins forever, i.e. a deadlock. Parallel is only safe on the initial, exclusive
        // startup pass; every rebuild after the first runtime unload uses a sequential pass.
        boolean parallelSafe = !JeiOptRuntimeState.hasRuntimeUnloadedOnce();
        try {
            partitioned = (parallelSafe ? allRecipes.parallelStream() : allRecipes.stream())
                .filter(validator::isRecipeValid)
                .collect(Collectors.partitioningBy(validator::isRecipeHandled));
        } catch (RuntimeException e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize parallel crafting recipe validation failed; falling back to sequential", e);
            partitioned = allRecipes.stream()
                .filter(validator::isRecipeValid)
                .collect(Collectors.partitioningBy(validator::isRecipeHandled));
        }

        JeiOptimize.LOGGER.info(
            "JEI Optimize parallel crafting recipes: {} handled + {} special from {} total in {} ms",
            partitioned.get(Boolean.TRUE).size(),
            partitioned.get(Boolean.FALSE).size(),
            allRecipes.size(),
            (System.nanoTime() - start) / 1_000_000L
        );
        return partitioned;
    }
}
