package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupContext;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
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
import mezz.jei.gui.search.ElementSearch;
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
        List<IListElementInfo<?>> safeIngredients = List.copyOf(ingredients);
        int total = safeIngredients.size();
        IElementSearch search = new ElementSearch(this.elementPrefixParser);
        IIngredientManager manager = this.ingredientManager;
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        java.util.concurrent.atomic.AtomicInteger nextStart = new java.util.concurrent.atomic.AtomicInteger();
        long generation = JeiOptRuntimeState.currentGeneration();
        long startNanos = System.nanoTime();
        JeiOptStartupProgressState.registerBuild(generation, chunkCount, total);
        JeiOptClientTickQueue.enqueue(() -> {
            try {
                if (!JeiOptRuntimeState.isCurrent(generation)) {
                    return true;
                }

                int start = nextStart.get();
                if (start < total) {
                    int end = Math.min(start + chunkSize, total);
                    List<IListElementInfo<?>> chunk = safeIngredients.subList(start, end);
                    for (IListElementInfo<?> info : chunk) {
                        updateHiddenStateEquivalent(info.getElement(), ingredientVisibility);
                    }
                    search.addAll(chunk, manager);
                    nextStart.set(end);
                    JeiOptStartupProgressState.markChunkCompleted(generation);
                    return false;
                }

                long publishStartNanos = System.nanoTime();
                this.elementSearch = search;
                this.invalidateCache();
                JeiOptStartupProgressState.markPublished(generation);
                JeiOptimize.LOGGER.info(
                    "JEI Optimize deferred ingredient filter build completed: {} ingredients ({} chunks, {} distinct uids) in {} ms; sidebar published in {} us",
                    total,
                    chunkCount,
                    search.getAllIngredients().size(),
                    (System.nanoTime() - startNanos) / 1_000_000L,
                    (System.nanoTime() - publishStartNanos) / 1_000L
                );
                return true;
            } catch (RuntimeException | LinkageError e) {
                JeiOptStartupProgressState.fail(generation, e);
                throw e;
            }
        });
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
        long generation = JeiOptRuntimeState.currentGeneration();
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        CompletableFuture<IElementSearch> future = AsyncIngredientFilterBuilder.buildChunkedAsync(
            ingredients,
            this.ingredientManager,
            ingredientVisibility,
            () -> new ElementSearch(this.elementPrefixParser),
            (search, chunk) -> search.addAll(chunk, this.ingredientManager),
            chunkSize,
            generation
        );
        long startNanos = System.nanoTime();
        JeiOptClientTickQueue.enqueue(() -> {
            try {
                return jeiopt$finalizeAsyncBuild(
                    future,
                    ingredients,
                    ingredientVisibility,
                    total,
                    chunkCount,
                    startNanos,
                    generation
                );
            } catch (RuntimeException | LinkageError e) {
                JeiOptStartupProgressState.fail(generation, e);
                throw e;
            }
        });
        JeiOptimize.LOGGER.info(
            "JEI Optimize async ingredient filter build submitted off-thread: {} ingredients in {} chunks",
            total,
            chunkCount
        );
    }

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<IElementSearch> future,
        List<IListElementInfo<?>> ingredients,
        IIngredientVisibility ingredientVisibility,
        int total,
        int chunkCount,
        long startNanos,
        long generation
    ) {
        // This filter belongs to a JEI runtime that has already been torn down; publishing into it
        // would resurrect the previous world's item list.
        if (!JeiOptRuntimeState.isCurrent(generation)) {
            future.cancel(false);
            JeiOptimize.LOGGER.debug("JEI Optimize discarded a stale async ingredient filter build");
            return true;
        }
        if (!future.isDone()) {
            return false;
        }
        if (future.isCancelled()) {
            JeiOptStartupProgressState.fail(
                generation,
                new java.util.concurrent.CancellationException("JEI ingredient filter build was cancelled")
            );
            return true;
        }
        IElementSearch built = null;
        try {
            built = future.join();
        } catch (RuntimeException e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize async ingredient filter build failed off-thread; falling back to synchronous build", e);
        }
        if (built != null) {
            long publishStartNanos = System.nanoTime();
            this.elementSearch = built;
            this.invalidateCache();
            JeiOptStartupProgressState.markPublished(generation);
            // getAllIngredients() is JEI's uid-keyed map, so it is normally smaller than the input.
            JeiOptimize.LOGGER.info(
                "JEI Optimize async ingredient filter build completed: {} ingredients ({} chunks, {} distinct uids) in {} ms; sidebar published in {} us",
                total,
                chunkCount,
                built.getAllIngredients().size(),
                (System.nanoTime() - startNanos) / 1_000_000L,
                (System.nanoTime() - publishStartNanos) / 1_000L
            );
        } else {
            for (IListElementInfo<?> info : ingredients) {
                updateHiddenStateEquivalent(info.getElement(), ingredientVisibility);
            }
            this.elementSearch.addAll(ingredients, this.ingredientManager);
            this.invalidateCache();
            JeiOptStartupProgressState.markReady(generation);
            JeiOptStartupProgressState.markPublished(generation);
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