package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncSortIndex;
import com.tonywww.jeioptimize.index.SortKey;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientSorter", remap = false)
public abstract class AsyncIngredientSorterMixin {
    @Inject(method = "sortIngredients", at = @At("RETURN"))
    private static void jeiOptimize$preheatSortIndex(
        Object clientConfig,
        Object modNameSortingConfig,
        Object ingredientTypeSortingConfig,
        Object ingredientManager,
        List<IListElementInfo<?>> ingredients,
        CallbackInfoReturnable<Comparator<?>> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.sortPreheat() || ingredients == null || ingredients.isEmpty()) {
            return;
        }

        List<SortKey<Integer>> sortKeys = new ArrayList<>(ingredients.size());
        for (int listIndex = 0; listIndex < ingredients.size(); listIndex++) {
            IListElementInfo<?> elementInfo = ingredients.get(listIndex);
            int sortedIndex = getSortedIndex(elementInfo, listIndex);
            sortKeys.add(new SortKey<>(listIndex, sortedIndex));
        }
        AsyncSortIndex.preheatIngredientSort(sortKeys);
    }

    private static int getSortedIndex(IListElementInfo<?> elementInfo, int fallback) {
        IListElement<?> element = elementInfo.getElement();
        return element == null ? fallback : element.getSortedIndex();
    }
}