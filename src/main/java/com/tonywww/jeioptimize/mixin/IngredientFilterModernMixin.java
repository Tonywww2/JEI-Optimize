package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Budgeted ingredient preparation for JEI builds that construct the whole index inside
 * {@code createElementSearch} instead of adding ingredients one by one. The final search uses
 * JEI's native batch factory so newer storage implementations are not populated incrementally.
 *
 * <p>{@link IngredientFilterMixin} handles the older shape; {@code JeiOptMixinPlugin} applies
 * exactly one of the two based on what the installed JEI actually declares. Nothing here captures
 * constructor arguments, so it survives further changes to the constructor signature.
 */
@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientFilter", remap = false)
public abstract class IngredientFilterModernMixin {
    @Shadow
    private IElementSearch elementSearch;

    @Shadow
    @Final
    private IIngredientVisibility ingredientVisibility;

    @Shadow
    public abstract void invalidateCache();

    @Invoker("createElementSearch")
    private static IElementSearch jeiopt$invokeCreateElementSearch(
        IClientConfig clientConfig,
        ElementPrefixParser elementPrefixParser,
        List<IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager
    ) {
        throw new AssertionError("replaced by mixin");
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lmezz/jei/gui/ingredients/IngredientFilter;createElementSearch("
                + "Lmezz/jei/common/config/IClientConfig;"
                + "Lmezz/jei/gui/search/ElementPrefixParser;"
                + "Ljava/util/List;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)"
                + "Lmezz/jei/gui/search/IElementSearch;"
        )
    )
    // Not static: Mixin matches the handler's modifier against the enclosing constructor, not
    // against the redirected static call.
    private IElementSearch jeiopt$deferElementSearch(
        IClientConfig clientConfig,
        ElementPrefixParser elementPrefixParser,
        List<IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager
    ) {
        if (clientConfig.isLowMemorySlowSearchEnabled()
            || !JeiOptExecutors.isJeiStartThread()
            || (!JeiOptFeatureFlags.asyncIngredientFilter() && !JeiOptFeatureFlags.deferredIngredientFilter())) {
            return jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, elementInfos, ingredientManager);
        }
        IElementSearch emptySearch = jeiopt$invokeCreateElementSearch(
            clientConfig,
            elementPrefixParser,
            List.of(),
            ingredientManager
        );
        JeiOptFilterBootstrap.capture(
            elementInfos,
            infos -> jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, infos, ingredientManager));
        return emptySearch;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void jeiopt$scheduleAsyncBuild(CallbackInfo callbackInfo) {
        JeiOptFilterBootstrap.Pending pending = JeiOptFilterBootstrap.take();
        if (pending == null) {
            return;
        }

        if (JeiOptFeatureFlags.asyncIngredientFilter() || JeiOptFeatureFlags.deferredIngredientFilter()) {
            jeiopt$scheduleBudgetedBuild(pending);
        }
    }

    private void jeiopt$scheduleBudgetedBuild(JeiOptFilterBootstrap.Pending pending) {
        int total = pending.ingredients().size();
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        long generation = JeiOptRuntimeState.currentGeneration();
        CompletableFuture<Void> preparation = AsyncIngredientFilterBuilder.prepareBudgetedAsync(
            pending.ingredients(),
            this.ingredientVisibility,
            chunkSize,
            generation
        );
        Supplier<IElementSearch> searchBuilder = () -> pending.searchFactory().apply(pending.ingredients());
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
            pending,
            total,
            chunkCount,
            startNanos,
            generation
        );
    }

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<Void> preparation,
        Supplier<IElementSearch> searchBuilder,
        JeiOptFilterBootstrap.Pending pending,
        int total,
        int chunkCount,
        long startNanos,
        long generation
    ) {
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
        if (built == null) {
            built = pending.searchFactory().apply(pending.ingredients());
            this.elementSearch = built;
            this.invalidateCache();
            JeiOptStartupProgressState.markReady(generation);
            JeiOptStartupProgressState.markPublished(generation);
            JeiOptimize.LOGGER.info(
                "JEI Optimize async ingredient filter fell back to synchronous build: {} ingredients", total);
            return true;
        }

        long publishStartNanos = System.nanoTime();
        JeiOptStartupProgressState.markReady(generation);
        this.elementSearch = built;
        this.invalidateCache();
        JeiOptStartupProgressState.markPublished(generation);
        JeiOptimize.LOGGER.info(
            "JEI Optimize budgeted ingredient filter build completed: {} ingredients ({} progress chunks, {} distinct uids) in {} ms; sidebar published in {} us",
            total,
            chunkCount,
            built.getAllIngredients().size(),
            (System.nanoTime() - startNanos) / 1_000_000L,
            (System.nanoTime() - publishStartNanos) / 1_000L
        );
        return true;
    }

}
