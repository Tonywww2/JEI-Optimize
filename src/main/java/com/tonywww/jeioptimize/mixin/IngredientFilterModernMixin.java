package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.common.config.IClientConfig;
import mezz.jei.gui.ingredients.IListElement;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Off-thread ingredient search index for JEI builds that construct the whole index inside
 * {@code createElementSearch} instead of adding ingredients one by one.
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
        if (!JeiOptFeatureFlags.asyncIngredientFilter() && !JeiOptFeatureFlags.deferredIngredientFilter()) {
            return jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, elementInfos, ingredientManager);
        }
        // The factory has to be built here: an @Invoker lives on the target class and cannot be
        // reached from outside it.
        JeiOptFilterBootstrap.capture(
            elementInfos,
            ingredientManager,
            infos -> jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, infos, ingredientManager));
        return jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, List.of(), ingredientManager);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void jeiopt$scheduleAsyncBuild(CallbackInfo callbackInfo) {
        JeiOptFilterBootstrap.Pending pending = JeiOptFilterBootstrap.take();
        if (pending == null) {
            return;
        }

        if (JeiOptFeatureFlags.asyncIngredientFilter()) {
            jeiopt$scheduleWorkerBuild(pending);
        } else if (JeiOptFeatureFlags.deferredIngredientFilter()) {
            jeiopt$scheduleDeferredBuild(pending);
        }
    }

    private void jeiopt$scheduleWorkerBuild(JeiOptFilterBootstrap.Pending pending) {
        int total = pending.ingredients().size();
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        long generation = JeiOptRuntimeState.currentGeneration();
        CompletableFuture<IElementSearch> future = AsyncIngredientFilterBuilder.buildChunkedAsync(
            pending.ingredients(),
            pending.ingredientManager(),
            this.ingredientVisibility,
            () -> pending.searchFactory().apply(List.of()),
            (search, chunk) -> jeiopt$addChunk(search, chunk, pending.ingredientManager()),
            chunkSize,
            generation
        );
        long startNanos = System.nanoTime();
        JeiOptClientTickQueue.enqueue(() -> {
            try {
                return jeiopt$finalizeAsyncBuild(future, pending, total, chunkCount, startNanos, generation);
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

    private void jeiopt$scheduleDeferredBuild(JeiOptFilterBootstrap.Pending pending) {
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int total = pending.ingredients().size();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        AtomicInteger nextStart = new AtomicInteger();
        long generation = JeiOptRuntimeState.currentGeneration();
        long startNanos = System.nanoTime();
        IElementSearch search = pending.searchFactory().apply(List.of());
        JeiOptStartupProgressState.registerBuild(generation, chunkCount, total);
        JeiOptClientTickQueue.enqueue(() -> {
            try {
                if (!JeiOptRuntimeState.isCurrent(generation)) {
                    return true;
                }

                int start = nextStart.get();
                if (start < total) {
                    int end = Math.min(start + chunkSize, total);
                    List<IListElementInfo<?>> chunk = pending.ingredients().subList(start, end);
                    for (IListElementInfo<?> info : chunk) {
                        jeiopt$updateHiddenState(info.getElement());
                    }
                    jeiopt$addChunk(search, chunk, pending.ingredientManager());
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

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<IElementSearch> future,
        JeiOptFilterBootstrap.Pending pending,
        int total,
        int chunkCount,
        long startNanos,
        long generation
    ) {
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
        this.elementSearch = built;
        this.invalidateCache();
        JeiOptStartupProgressState.markPublished(generation);
        JeiOptimize.LOGGER.info(
            "JEI Optimize async ingredient filter build completed: {} ingredients ({} chunks, {} distinct uids) in {} ms; sidebar published in {} us",
            total,
            chunkCount,
            built.getAllIngredients().size(),
            (System.nanoTime() - startNanos) / 1_000_000L,
            (System.nanoTime() - publishStartNanos) / 1_000L
        );
        return true;
    }

    private void jeiopt$updateHiddenState(IListElement<?> element) {
        ITypedIngredient<?> typedIngredient = element.getTypedIngredient();
        boolean visible = this.ingredientVisibility.isIngredientVisible(typedIngredient);
        if (element.isVisible() != visible) {
            element.setVisible(visible);
        }
    }

    private static void jeiopt$addChunk(
        IElementSearch search,
        List<IListElementInfo<?>> chunk,
        IIngredientManager ingredientManager
    ) {
        for (IListElementInfo<?> info : chunk) {
            jeiopt$addElement(search, info, ingredientManager);
        }
    }

    private static <T> void jeiopt$addElement(
        IElementSearch search,
        IListElementInfo<T> info,
        IIngredientManager ingredientManager
    ) {
        search.add(info, ingredientManager);
    }
}
