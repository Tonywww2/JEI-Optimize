package com.tonywww.jeioptimize.snapshot;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
//? if forge {
import mezz.jei.library.ingredients.IIngredientSupplier;
//?} else {
/*import mezz.jei.api.ingredients.IIngredientSupplier;
*///?}
import mezz.jei.library.util.IngredientSupplierHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class RecipeIndexSnapshotBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeIndexSnapshotBuilder.class);

    private final IIngredientManager ingredientManager;

    public RecipeIndexSnapshotBuilder(IIngredientManager ingredientManager) {
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
    }

    public static <T> List<RecipeIndexSnapshot<T>> build(
        IIngredientManager ingredientManager,
        IRecipeCategory<T> recipeCategory,
        Collection<T> recipes,
        Collection<T> hiddenRecipes
    ) {
        return new RecipeIndexSnapshotBuilder(ingredientManager).buildForCategory(recipeCategory, recipes, hiddenRecipes);
    }

    public <T> List<RecipeIndexSnapshot<T>> buildForCategory(
        IRecipeCategory<T> recipeCategory,
        Collection<T> recipes,
        Collection<T> hiddenRecipes
    ) {
        if (!JeiOptFeatureFlags.recipeFocusPreheat() && !JeiOptFeatureFlags.catalystPreheat()) {
            return List.of();
        }
        Objects.requireNonNull(recipeCategory, "recipeCategory");
        if (recipes == null || recipes.isEmpty()) {
            return List.of();
        }

        Set<T> hiddenRecipeSet = hiddenRecipes == null ? Set.of() : Set.copyOf(hiddenRecipes);
        List<RecipeIndexSnapshot<T>> snapshots = new ArrayList<>(recipes.size());
        for (T recipe : recipes) {
            createSnapshot(recipeCategory, recipe, hiddenRecipeSet.contains(recipe)).ifPresent(snapshots::add);
        }
        return List.copyOf(snapshots);
    }

    private <T> Optional<RecipeIndexSnapshot<T>> createSnapshot(IRecipeCategory<T> recipeCategory, T recipe, boolean hidden) {
        try {
            IIngredientSupplier ingredientSupplier = IngredientSupplierHelper.getIngredientSupplier(recipe, recipeCategory, ingredientManager);
            if (ingredientSupplier == null) {
                return Optional.empty();
            }

            Map<RecipeIngredientRole, Set<Object>> roleToIngredientUids = new EnumMap<>(RecipeIngredientRole.class);
            for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
                Set<Object> uids = getIngredientUids(ingredientSupplier, role);
                if (!uids.isEmpty()) {
                    roleToIngredientUids.put(role, Set.copyOf(uids));
                }
            }

            return Optional.of(new RecipeIndexSnapshot<>(
                recipeCategory.getRecipeType(),
                recipe,
                Map.copyOf(roleToIngredientUids),
                hidden
            ));
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Failed to create JEI recipe index snapshot for {}", recipeCategory.getRecipeType().getUid(), e);
            return Optional.empty();
        }
    }

    private Set<Object> getIngredientUids(IIngredientSupplier ingredientSupplier, RecipeIngredientRole role) {
        Collection<ITypedIngredient<?>> ingredients = ingredientSupplier.getIngredients(role);
        if (ingredients.isEmpty()) {
            return Set.of();
        }

        Set<Object> uids = new LinkedHashSet<>();
        for (ITypedIngredient<?> ingredient : ingredients) {
            getIngredientUid(ingredient).ifPresent(uids::add);
        }
        return uids;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private Optional<Object> getIngredientUid(ITypedIngredient<?> typedIngredient) {
        try {
            IIngredientHelper ingredientHelper = ingredientManager.getIngredientHelper(typedIngredient.getType());
            Object uid = ingredientHelper.getUniqueId(typedIngredient.getIngredient(), UidContext.Recipe);
            return Optional.ofNullable(uid);
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Failed to get JEI recipe ingredient UID", e);
            return Optional.empty();
        }
    }
}