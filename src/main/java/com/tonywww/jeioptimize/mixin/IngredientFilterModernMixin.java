package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.JeiOptMixinPlugin;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.index.DeferredNativeSearchStorage;
import com.tonywww.jeioptimize.index.TooltipSearchShadow;
import com.tonywww.jeioptimize.index.TooltipSearchAdapter;
import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy;
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

/**
 * Budgeted ingredient indexing for JEI builds that construct the whole index inside
 * {@code createElementSearch}. The redirect creates one empty search, then client ticks populate
 * that isolated instance before the filter can be published.
 *
 * <p>{@link IngredientFilterMixin} handles the older shape; {@code JeiOptMixinPlugin} applies
 * exactly one of the two based on what the installed JEI actually declares. Nothing here captures
 * constructor arguments. The mixin plugin verifies the exact constructor before applying it.
 * The comparator selector and the JEI 15.62 sorting-config selector are both accepted.
 * An unknown constructor keeps the disable log and native construction.
 *
 * <p>On the sorting-config constructor, JEI already applied type visibility before this mixin
 * schedules the tick build. That path must not let the weaker tick visibility check show those
 * elements again. The comparator path keeps the previous tick behavior.
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
    @Final
    private ElementPrefixParser elementPrefixParser;

    @Shadow
    @Final
    private IClientConfig clientConfig;

    @Shadow
    public abstract void invalidateCache();

    @Shadow
    public abstract void rebuildItemFilter();

    @Invoker("notifyListenersOfChange")
    protected abstract void jeiopt$notifyTooltipReady();

    @Invoker("createElementSearch")
    private static IElementSearch jeiopt$invokeCreateElementSearch(
        IClientConfig clientConfig,
        ElementPrefixParser elementPrefixParser,
        List<IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager
    ) {
        throw new AssertionError("replaced by mixin");
    }

    private boolean jeiopt$preserveConstructorHidden;
    private boolean jeiopt$redirectEntered;

    @Redirect(
        method = {
            JeiOptMixinPlugin.COMPARATOR_INGREDIENT_FILTER_CONSTRUCTOR,
            JeiOptMixinPlugin.SORTING_CONFIG_INGREDIENT_FILTER_CONSTRUCTOR
        },
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
        this.jeiopt$redirectEntered = true;
        boolean canDeferFilter = JeiOptFilterBootstrap.canDeferFilter();
        boolean asyncOrDeferred = JeiOptFeatureFlags.asyncIngredientFilter() || JeiOptFeatureFlags.deferredIngredientFilter();
        boolean canDeferTooltip = JeiOptFilterBootstrap.canDeferTooltip();
        boolean tooltipMetrics = JeiOptFeatureFlags.tooltipSearchMetrics();
        switch (BudgetedIngredientFilterPolicy.captureChoice(
            TooltipSearchAdapter.lowMemory(clientConfig),
            canDeferFilter,
            JeiOptExecutors.isJeiStartThread(),
            asyncOrDeferred,
            canDeferTooltip,
            tooltipMetrics
        )) {
            case LOW_MEMORY -> {
                return jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, elementInfos, ingredientManager);
            }
            case FULL_NATIVE -> {
                return TooltipSearchShadow.observeNativeBuild(elementInfos, elementPrefixParser, () ->
                    jeiopt$invokeCreateElementSearch(clientConfig, elementPrefixParser, elementInfos, ingredientManager));
            }
            case EMPTY_WITH_STORAGES -> {
                var captured = DeferredNativeSearchStorage.capture(() -> jeiopt$invokeCreateElementSearch(
                    clientConfig, elementPrefixParser, List.of(), ingredientManager));
                JeiOptFilterBootstrap.capture(elementInfos, ingredientManager, captured.value(), captured.storages());
                return captured.value();
            }
            case EMPTY -> {
                IElementSearch emptySearch = jeiopt$invokeCreateElementSearch(
                    clientConfig, elementPrefixParser, List.of(), ingredientManager);
                JeiOptFilterBootstrap.capture(elementInfos, ingredientManager, emptySearch);
                return emptySearch;
            }
        }
        throw new IllegalStateException("Unhandled ingredient filter capture choice");
    }

    @Inject(method = JeiOptMixinPlugin.COMPARATOR_INGREDIENT_FILTER_CONSTRUCTOR, at = @At("RETURN"))
    private void jeiopt$scheduleAsyncBuild(CallbackInfo callbackInfo) {
        jeiopt$preserveConstructorHidden = false;
        jeiopt$scheduleCapturedBuild("comparator");
    }

    @Inject(method = JeiOptMixinPlugin.SORTING_CONFIG_INGREDIENT_FILTER_CONSTRUCTOR, at = @At("RETURN"))
    private void jeiopt$scheduleSortingConfigBuild(CallbackInfo callbackInfo) {
        jeiopt$preserveConstructorHidden = true;
        jeiopt$scheduleCapturedBuild("sorting-config");
    }

    private void jeiopt$scheduleCapturedBuild(String branch) {
        boolean redirectEntered = this.jeiopt$redirectEntered;
        this.jeiopt$redirectEntered = false;
        boolean sortingConfig = "sorting-config".equals(branch);
        JeiOptFilterBootstrap.Pending pending = redirectEntered ? JeiOptFilterBootstrap.take() : null;
        switch (BudgetedIngredientFilterPolicy.observe(redirectEntered, pending != null)) {
            case NOT_CONFIRMED -> {
                JeiOptimize.LOGGER.warn(
                    "JEI budgeted ingredient filter constructor {} passed the contract check, but the createElementSearch redirect did not run; retaining JEI construction",
                    branch
                );
                return;
            }
            case RAN_WITHOUT_BUDGETED_CAPTURE -> {
                JeiOptimize.LOGGER.info(
                    "JEI budgeted ingredient filter redirect ran on branch={}, but no budgeted build was captured",
                    branch
                );
                return;
            }
            case BUDGETED_BUILD_SCHEDULED -> {
            }
        }
        boolean canDeferFilter = JeiOptFilterBootstrap.canDeferFilter();
        boolean asyncOrDeferred = JeiOptFeatureFlags.asyncIngredientFilter()
            || JeiOptFeatureFlags.deferredIngredientFilter();
        boolean canDeferTooltip = JeiOptFilterBootstrap.canDeferTooltip();
        if (BudgetedIngredientFilterPolicy.rejectTooltipSearchIndex(
            sortingConfig, JeiOptFeatureFlags.tooltipSearchIndex())
            && (canDeferFilter || asyncOrDeferred)) {
            JeiOptimize.LOGGER.warn(
                "JEI tooltip search index rejected on sorting-config constructor: type visibility is not handled; retaining budgeted ingredient filter"
            );
        }
        JeiOptimize.LOGGER.info("JEI Optimize budgeted ingredient filter redirect confirmed: branch={}", branch);
        switch (BudgetedIngredientFilterPolicy.scheduleChoice(
            sortingConfig,
            true,
            canDeferTooltip,
            canDeferFilter,
            asyncOrDeferred
        )) {
            case TOOLTIP -> {
                JeiOptFilterBootstrap.scheduleTooltip(pending.ingredients(), pending.ingredientManager(), this.ingredientVisibility,
                    this.elementPrefixParser, pending.emptySearch(),
                    () -> jeiopt$invokeCreateElementSearch(this.clientConfig, this.elementPrefixParser, List.of(), pending.ingredientManager()),
                    () -> jeiopt$invokeCreateElementSearch(this.clientConfig, this.elementPrefixParser, pending.ingredients(), pending.ingredientManager()),
                    search -> this.elementSearch = search,
                    this::rebuildItemFilter,
                    () -> { invalidateCache(); jeiopt$notifyTooltipReady(); });
                return;
            }
            case NATIVE -> {
                JeiOptFilterBootstrap.scheduleNative(pending.ingredients(), pending.ingredientManager(), this.ingredientVisibility,
                    this.elementPrefixParser, pending.emptySearch(), search -> this.elementSearch = search,
                    this::rebuildItemFilter, () -> { invalidateCache(); jeiopt$notifyTooltipReady(); }, pending.storages(),
                    this.jeiopt$preserveConstructorHidden);
                return;
            }
            case BUDGETED_AWAIT -> {
                jeiopt$scheduleBudgetedBuild(pending, branch);
                return;
            }
            case CAPTURED_WITHOUT_JOB -> throw new IllegalStateException(
                "JEI budgeted ingredient filter captured an empty search on branch " + branch + " but scheduled no build");
            case NO_CAPTURE -> throw new IllegalStateException(
                "JEI budgeted ingredient filter lost a captured search on branch " + branch);
        }
    }

    private void jeiopt$scheduleBudgetedBuild(JeiOptFilterBootstrap.Pending pending, String branch) {
        int total = pending.ingredients().size();
        int chunkSize = JeiOptFeatureFlags.ingredientFilterChunkSize();
        int chunkCount = (total + chunkSize - 1) / Math.max(1, chunkSize);
        long generation = JeiOptRuntimeState.currentGeneration();
        CompletableFuture<IElementSearch> future = AsyncIngredientFilterBuilder.buildBudgetedAsync(
            pending.ingredients(),
            this.ingredientVisibility,
            pending.emptySearch(),
            (search, element) -> jeiopt$addElement(search, element, pending.ingredientManager()),
            chunkSize,
            generation,
            this.elementPrefixParser,
            this.jeiopt$preserveConstructorHidden
        );
        long startNanos = System.nanoTime();
        JeiOptimize.LOGGER.info(
            "JEI Optimize budgeted ingredient filter build scheduled: branch={}, {} ingredients in {} progress chunks",
            branch,
            total,
            chunkCount
        );
        try {
            JeiOptExecutors.awaitJeiStartTask(future);
        } catch (RuntimeException | LinkageError failure) {
            future.cancel(false);
            if (JeiOptExecutors.isJeiStartCancellation(failure)
                || !JeiOptRuntimeState.isCurrent(generation)) {
                JeiOptimize.LOGGER.debug("JEI Optimize discarded a budgeted ingredient filter build without publishing");
                throw failure;
            }
            jeiopt$abortUnpublishedBuild(future, generation, failure);
            throw failure;
        }
        switch (BudgetedIngredientFilterPolicy.publication(
            JeiOptRuntimeState.isCurrent(generation),
            future.isCancelled(),
            BudgetedIngredientFilterPolicy.publishable(
                future.isDone(), future.isCancelled(), future.isCompletedExceptionally())
        )) {
            case DISCARD_STALE_OR_CANCELLED -> {
                future.cancel(false);
                JeiOptimize.LOGGER.debug("JEI Optimize discarded a stale async ingredient filter build");
                return;
            }
            case ABORT_UNPUBLISHED -> {
                IllegalStateException failure = new IllegalStateException(
                    "JEI Optimize budgeted ingredient filter had no completed index to publish");
                jeiopt$abortUnpublishedBuild(future, generation, failure);
                throw failure;
            }
            case PUBLISH -> {
            }
        }
        jeiopt$finalizeAsyncBuild(
            future,
            total,
            chunkCount,
            startNanos,
            generation
        );
    }

    private void jeiopt$abortUnpublishedBuild(CompletableFuture<?> future, long generation, Throwable failure) {
        future.cancel(false);
        JeiOptimize.LOGGER.error(
            "JEI Optimize budgeted ingredient filter build failed; empty or partial index will not be published",
            failure
        );
        JeiOptStartupProgressState.fail(generation, failure);
    }

    private boolean jeiopt$finalizeAsyncBuild(
        CompletableFuture<IElementSearch> future,
        int total,
        int chunkCount,
        long startNanos,
        long generation
    ) {
        if (!JeiOptRuntimeState.isCurrent(generation) || future.isCancelled()) {
            future.cancel(false);
            JeiOptimize.LOGGER.debug("JEI Optimize discarded a stale async ingredient filter build");
            return true;
        }
        if (!BudgetedIngredientFilterPolicy.publishable(
            future.isDone(), future.isCancelled(), future.isCompletedExceptionally())) {
            IllegalStateException failure = new IllegalStateException(
                "JEI Optimize budgeted ingredient filter had no completed index to publish");
            jeiopt$abortUnpublishedBuild(future, generation, failure);
            throw failure;
        }

        try {
            IElementSearch built = future.join();
            long publishStartNanos = System.nanoTime();
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
        } catch (RuntimeException | LinkageError e) {
            jeiopt$abortUnpublishedBuild(future, generation, e);
            throw e;
        }
        return true;
    }

    private static <T> void jeiopt$addElement(
        IElementSearch search,
        IListElementInfo<T> info,
        IIngredientManager ingredientManager
    ) {
        search.add(info, ingredientManager);
    }

}
