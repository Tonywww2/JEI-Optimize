package com.tonywww.jeioptimize.integration;

//? if forge {
import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtonsFactory;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.layouts.RecipeLayoutDrawableErrored;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LegacyLazyRecipeLayoutList<T> implements IRecipeLayoutList {
    private static final int RECIPE_BORDER_PADDING = 4;
    private static final int MAX_CACHED_LAYOUTS = 512;

    private final IRecipeManager recipeManager;
    private final IRecipeLayoutWithButtonsFactory layoutFactory;
    private final IRecipeCategory<T> recipeCategory;
    private final List<T> recipes;
    private final IFocusGroup focuses;
    private final AbstractContainerMenu container;
    private final Player player;
    private final Map<Integer, RecipeLayoutWithButtons<T>> layouts = new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, RecipeLayoutWithButtons<T>> eldest) {
            return size() > MAX_CACHED_LAYOUTS;
        }
    };

    public LegacyLazyRecipeLayoutList(
        IRecipeManager recipeManager,
        IRecipeLayoutWithButtonsFactory layoutFactory,
        IRecipeCategory<T> recipeCategory,
        List<T> recipes,
        IFocusGroup focuses,
        AbstractContainerMenu container,
        Player player
    ) {
        this.recipeManager = recipeManager;
        this.layoutFactory = layoutFactory;
        this.recipeCategory = recipeCategory;
        this.recipes = List.copyOf(recipes);
        this.focuses = focuses;
        this.container = container;
        this.player = player;
    }

    @Override
    public int size() {
        return recipes.size();
    }

    @Override
    public List<RecipeLayoutWithButtons<?>> subList(int fromIndex, int toIndex) {
        List<RecipeLayoutWithButtons<?>> visible = new ArrayList<>(toIndex - fromIndex);
        for (int index = fromIndex; index < toIndex; index++) {
            visible.add(get(index));
        }
        return visible;
    }

    @Override
    public Optional<RecipeLayoutWithButtons<?>> findFirst() {
        return recipes.isEmpty() ? Optional.empty() : Optional.of(get(0));
    }

    @Override
    public void tick() {
        for (RecipeLayoutWithButtons<T> layout : layouts.values()) {
            layout.tick(container, player);
        }
    }

    private RecipeLayoutWithButtons<T> get(int index) {
        return layouts.computeIfAbsent(index, this::create);
    }

    private RecipeLayoutWithButtons<T> create(int index) {
        T recipe = recipes.get(index);
        DrawableNineSliceTexture background = Internal.getTextures().getRecipeBackground();
        try {
            IRecipeLayoutDrawable<T> drawable = recipeManager
                .createRecipeLayoutDrawable(recipeCategory, recipe, focuses, background, RECIPE_BORDER_PADDING)
                .orElseGet(() -> new RecipeLayoutDrawableErrored<>(
                    recipeCategory,
                    recipe,
                    background,
                    RECIPE_BORDER_PADDING
                ));
            return layoutFactory.create(drawable);
        } catch (RuntimeException | LinkageError error) {
            JeiOptimize.LOGGER.error("Failed to create a lazy JEI recipe layout", error);
            return layoutFactory.create(new RecipeLayoutDrawableErrored<>(
                recipeCategory,
                recipe,
                background,
                RECIPE_BORDER_PADDING
            ));
        }
    }
}
//?} else {
/*public final class LegacyLazyRecipeLayoutList {
    private LegacyLazyRecipeLayoutList() {
    }
}
*///?}