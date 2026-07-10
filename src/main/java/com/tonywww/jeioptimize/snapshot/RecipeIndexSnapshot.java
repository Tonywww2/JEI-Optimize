package com.tonywww.jeioptimize.snapshot;

import java.util.Map;
import java.util.Set;

import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

public record RecipeIndexSnapshot<T>(
    RecipeType<T> recipeType,
    T recipe,
    Map<RecipeIngredientRole, Set<Object>> roleToIngredientUids,
    boolean hidden
) {
}