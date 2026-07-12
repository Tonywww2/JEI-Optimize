package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptStartupContext;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.common.config.IClientToggleState;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.filter.IFilterTextSource;
import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public abstract class IngredientFilterMixin {
    @Shadow
    private IElementSearch elementSearch;

    @Shadow
    @Final
    private ElementPrefixParser elementPrefixParser;

    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Shadow
    public abstract <V> void addIngredient(IListElementInfo<V> info);

    @Shadow
    public abstract void invalidateCache();

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/ingredients/IngredientFilter;addIngredient(Lmezz/jei/gui/ingredients/IListElementInfo;)V"
        )
    )
    private void jeiopt$skipIndividualAddDuringConstruction(IngredientFilter instance, IListElementInfo<?> ingredientInfo) {
        if (!JeiOptFeatureFlags.batchIngredientFilterInit()
            && !JeiOptFeatureFlags.deferredIngredientFilter()
            && !JeiOptFeatureFlags.asyncIngredientFilter()) {
            addIngredient(ingredientInfo);
        }
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void jeiopt$batchAddAfterConstruction(
        IFilterTextSource filterTextSource,
        IClientConfig clientConfig,
        IIngredientFilterConfig config,
        IIngredientManager ingredientManager,
        Comparator<?> ingredientComparator,
        List<IListElementInfo<?>> ingredients,
        IModIdHelper modIdHelper,
        IIngredientVisibility ingredientVisibility,
        IColorHelper colorHelper,
        IClientToggleState clientToggleState,
        CallbackInfo callbackInfo
    ) {
        if (JeiOptFeatureFlags.asyncIngredientFilter()) {
            jeiopt$scheduleAsyncBuild(ingredients, ingredientVisibility);
        } else if (JeiOptFeatureFlags.deferredIngredientFilter()) {
            jeiopt$scheduleDeferredBuild(ingredients, ingredientVisibility);
        } else if (JeiOptFeatureFlags.batchIngredientFilterInit()) {
            for (IListElementInfo<?> ingredient : ingredients) {
                updateHiddenStateEquivalent(ingredient.getElement(), ingredientVisibility);
            }
            elementSearch.addAll(ingredients, this.ingredientManager);
            invalidateCache();
        }

        if (JeiOptFeatureFlags.searchPreheat()) {
            JeiOptStartupContext.captureIngredientFilter(
                this.elementSearch,
                ingredients,
                this.ingredientManager,
                config,
                colorHelper
            );
        }
    }

    private void jeiopt$scheduleDeferredBuild(List<IListElementInfo<?>> ingredients, IIngredientVisibility ingredientVisibility) {
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int total = ingredients.size();
        IElementSearch search = this.elementSearch;
        IIngredientManager manager = this.ingredientManager;
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(chunkCount);
        long startNanos = System.nanoTime();
        for (int start = 0; start < total; start += chunkSize) {
            int end = Math.min(start + chunkSize, total);
            List<IListElementInfo<?>> chunk = List.copyOf(ingredients.subList(start, end));
            JeiOptClientTickQueue.enqueue(() -> {
                for (IListElementInfo<?> info : chunk) {
                    updateHiddenStateEquivalent(info.getElement(), ingredientVisibility);
                }
                search.addAll(chunk, manager);
                this.invalidateCache();
                if (remaining.decrementAndGet() == 0) {
                    JeiOptimize.LOGGER.info(
                        "JEI Optimize deferred ingredient filter build completed: {} ingredients in {} ms after world entry",
                        total,
                        (System.nanoTime() - startNanos) / 1_000_000L
                    );
                }
                return true;
            });
        }
        JeiOptimize.LOGGER.info(
            "JEI Optimize deferred ingredient filter build scheduled: {} ingredients in {} chunks",
            total,
            chunkCount
        );
    }

    private void jeiopt$scheduleAsyncBuild(
        List<IListElementInfo<?>> ingredients,
        IIngredientVisibility ingredientVisibility
    ) {
        int total = ingredients.size();
        CompletableFuture<IElementSearch> future = AsyncIngredientFilterBuilder.buildAsync(
            ingredients, this.ingredientManager, this.elementPrefixParser, ingredientVisibility);
        long startNanos = System.nanoTime();
        JeiOptClientTickQueue.enqueue(() ->
            jeiopt$finalizeAsyncBuild(future, ingredients, ingredientVisibility, total, startNanos));
        JeiOptimize.LOGGER.info(
            "JEI Optimize async ingredient filter build submitted off-thread: {} ingredients",
            total
        );
    }

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<IElementSearch> future,
        List<IListElementInfo<?>> ingredients,
        IIngredientVisibility ingredientVisibility,
        int total,
        long startNanos
    ) {
        if (!future.isDone()) {
            return false;
        }
        IElementSearch built = null;
        try {
            built = future.join();
        } catch (RuntimeException e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize async ingredient filter build failed off-thread; falling back to synchronous build", e);
        }
        if (built != null) {
            this.elementSearch = built;
            this.invalidateCache();
            JeiOptimize.LOGGER.info(
                "JEI Optimize async ingredient filter build completed: {} indexed (input {}) in {} ms after world entry",
                built.getAllIngredients().size(),
                total,
                (System.nanoTime() - startNanos) / 1_000_000L
            );
        } else {
            for (IListElementInfo<?> info : ingredients) {
                updateHiddenStateEquivalent(info.getElement(), ingredientVisibility);
            }
            this.elementSearch.addAll(ingredients, this.ingredientManager);
            this.invalidateCache();
            JeiOptimize.LOGGER.info(
                "JEI Optimize async ingredient filter fell back to synchronous build: {} ingredients",
                total
            );
        }
        return true;
    }

    private static void updateHiddenStateEquivalent(IListElement<?> element, IIngredientVisibility ingredientVisibility) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        boolean visible = ingredientVisibility.isIngredientVisible(typedIngredient);
        if (element.isVisible() != visible) {
            element.setVisible(visible);
        }
    }
}