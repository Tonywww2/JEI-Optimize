package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
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
import java.util.function.Supplier;

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
    @Final
    private IClientConfig clientConfig;

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
        boolean deferToStartupThread = JeiOptExecutors.isJeiStartThread()
            && (JeiOptFeatureFlags.deferredIngredientFilter() || JeiOptFeatureFlags.asyncIngredientFilter());
        if (this.clientConfig.isLowMemorySlowSearchEnabled()
            || (!JeiOptFeatureFlags.batchIngredientFilterInit() && !deferToStartupThread)) {
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
        if (this.clientConfig.isLowMemorySlowSearchEnabled()) {
            JeiOptimize.LOGGER.info("JEI Optimize retained JEI's low-memory ingredient search");
        } else if (JeiOptExecutors.isJeiStartThread()
            && (JeiOptFeatureFlags.asyncIngredientFilter() || JeiOptFeatureFlags.deferredIngredientFilter())) {
            jeiopt$scheduleAsyncBuild(ingredients, ingredientVisibility);
        } else if (JeiOptFeatureFlags.batchIngredientFilterInit()) {
            for (IListElementInfo<?> ingredient : ingredients) {
                updateHiddenStateEquivalent(ingredient.getElement(), ingredientVisibility);
            }
            elementSearch.addAll(ingredients, this.ingredientManager);
            invalidateCache();
        }

        if (JeiOptFeatureFlags.searchPreheat() && !this.clientConfig.isLowMemorySlowSearchEnabled()) {
            JeiOptStartupContext.captureIngredientFilter(
                this.elementSearch,
                ingredients,
                this.ingredientManager,
                config,
                colorHelper
            );
        }
    }

    private void jeiopt$scheduleAsyncBuild(
        List<IListElementInfo<?>> ingredients,
        IIngredientVisibility ingredientVisibility
    ) {
        int total = ingredients.size();
        long generation = JeiOptRuntimeState.currentGeneration();
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        IElementSearch targetSearch = this.elementSearch;
        CompletableFuture<Void> preparation = AsyncIngredientFilterBuilder.prepareBudgetedAsync(
            ingredients,
            ingredientVisibility,
            chunkSize,
            generation
        );
        Supplier<IElementSearch> searchBuilder = () -> {
            targetSearch.addAll(ingredients, this.ingredientManager);
            return targetSearch;
        };
        long startNanos = System.nanoTime();
        JeiOptimize.LOGGER.info(
            "JEI Optimize budgeted ingredient filter preparation scheduled: {} ingredients in {} progress chunks",
            total,
            chunkCount
        );
        try {
            JeiOptExecutors.awaitJeiStartTask(preparation);
        } catch (RuntimeException | LinkageError e) {
            if (JeiOptExecutors.isJeiStartCancellation(e)
                || !JeiOptRuntimeState.isCurrent(generation)) {
                throw e;
            }
        }
        jeiopt$finalizeAsyncBuild(
            preparation,
            searchBuilder,
            ingredients,
            ingredientVisibility,
            total,
            chunkCount,
            startNanos,
            generation
        );
    }

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<Void> preparation,
        Supplier<IElementSearch> searchBuilder,
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
            preparation.cancel(false);
            JeiOptimize.LOGGER.debug("JEI Optimize discarded a stale async ingredient filter build");
            return true;
        }
        if (!preparation.isDone()) {
            return false;
        }
        if (preparation.isCancelled()) {
            JeiOptStartupProgressState.fail(
                generation,
                new java.util.concurrent.CancellationException("JEI ingredient filter build was cancelled")
            );
            return true;
        }
        IElementSearch built = null;
        try {
            preparation.join();
            built = searchBuilder.get();
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize ingredient filter preparation failed; falling back to a fresh native batch build", e);
        }
        if (built != null) {
            long publishStartNanos = System.nanoTime();
            JeiOptStartupProgressState.markReady(generation);
            this.elementSearch = built;
            this.invalidateCache();
            JeiOptStartupProgressState.markPublished(generation);
            // getAllIngredients() is JEI's uid-keyed map, so it is normally smaller than the input.
            JeiOptimize.LOGGER.info(
                "JEI Optimize budgeted ingredient filter build completed: {} ingredients ({} progress chunks, {} distinct uids) in {} ms; sidebar published in {} us",
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
            IElementSearch fallback = new ElementSearch(this.elementPrefixParser);
            fallback.addAll(ingredients, this.ingredientManager);
            this.elementSearch = fallback;
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