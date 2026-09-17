package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.index.DeferredNativeSearchStorage;
import com.tonywww.jeioptimize.index.TooltipFilterBuild;
import com.tonywww.jeioptimize.index.TooltipSearchAdapter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Hands the ingredient list and isolated empty search from a constructor redirect to the matching
 * {@code RETURN} callback.
 *
 * <p>Newer JEI builds construct the whole search index inside one private factory call, so the
 * only way to defer that work is to intercept the call itself. The callback that schedules the
 * client-tick build cannot capture the constructor arguments without binding to a specific JEI
 * constructor descriptor, so the two halves meet here instead. JEI builds one filter at a time,
 * so a single slot is enough.
 */
public final class JeiOptFilterBootstrap {
    private static volatile Pending pending;
    private static final JeiOptFilterBuildGate GATE = new JeiOptFilterBuildGate();
    private static final ThreadLocal<Boolean> GUI_REGISTRATION = new ThreadLocal<>();
    private static volatile StartupTiming startupTiming;
    private static final java.util.concurrent.atomic.AtomicLong RESOURCE_REVISION = new java.util.concurrent.atomic.AtomicLong();

    private JeiOptFilterBootstrap() {
    }

    public static void begin(long generation) {
        GATE.begin(generation);
        startupTiming = new StartupTiming(generation);
    }

    public static void runtimePublished() {
        StartupTiming timing = startupTiming;
        if (timing != null && JeiOptRuntimeState.isCurrent(timing.generation)) {
            timing.published = true;
            JeiOptimize.LOGGER.info(
                "JEI startup runtime timing: generation={}, runtimePublishedAtEpochMs={}, startupToRuntimePublishedMs={}, sidebarInteractive=notMeasured",
                timing.generation, System.currentTimeMillis(), (System.nanoTime() - timing.started) / 1_000_000L);
        }
    }

    public static void clientTickFinished(long tickStarted) {
        StartupTiming timing = startupTiming;
        if (timing == null || tickStarted <= 0) {
            return;
        }
        long elapsed = System.nanoTime() - tickStarted;
        timing.ticks++;
        timing.totalTickNanos += elapsed;
        timing.maxTickNanos = Math.max(timing.maxTickNanos, elapsed);
        if (timing.published) {
            JeiOptimize.LOGGER.info(
                "JEI startup client tick timing: generation={}, fullClientTicks={}, totalClientTickMs={}, maxClientTickMs={}",
                timing.generation, timing.ticks, timing.totalTickNanos / 1_000_000L, timing.maxTickNanos / 1_000_000L);
            startupTiming = null;
        }
    }

    public static long resourceRevision() {
        return RESOURCE_REVISION.get();
    }

    public static void resourcesChanged() {
        RESOURCE_REVISION.incrementAndGet();
    }

    public static void runGuiRegistration(Runnable callback) {
        GUI_REGISTRATION.set(true);
        try {
            callback.run();
        } finally {
            GUI_REGISTRATION.remove();
        }
    }

    public static boolean canDeferTooltip() {
        return canDeferFilter() && JeiOptFeatureFlags.tooltipSearchIndex()
            && JeiOptCompatibilityState.tooltipPrefix() != 0;
    }

    public static boolean canDeferFilter() {
        return Boolean.TRUE.equals(GUI_REGISTRATION.get()) && Minecraft.getInstance().isSameThread()
            && JeiOptFeatureFlags.asyncStartup()
            && (JeiOptFeatureFlags.asyncIngredientFilter() || JeiOptFeatureFlags.deferredIngredientFilter());
    }

    public static void awaitBuilds() {
        if (JeiOptExecutors.isJeiStartThread()) {
            JeiOptExecutors.awaitJeiStartTask(GATE.completion(JeiOptRuntimeState.currentGeneration()));
        }
    }

    public static void scheduleNative(List<? extends IListElementInfo<?>> infos, IIngredientManager manager,
        IIngredientVisibility visibility, ElementPrefixParser parser, IElementSearch empty,
        Consumer<IElementSearch> publish, Runnable rebuild, Runnable refresh) {
        scheduleNative(infos, manager, visibility, parser, empty, publish, rebuild, refresh, List.of());
    }

    public static void scheduleNative(List<? extends IListElementInfo<?>> infos, IIngredientManager manager,
        IIngredientVisibility visibility, ElementPrefixParser parser, IElementSearch empty,
        Consumer<IElementSearch> publish, Runnable rebuild, Runnable refresh, List<DeferredNativeSearchStorage> storages) {
        long generation = JeiOptRuntimeState.currentGeneration();
        long initialResources = resourceRevision();
        String initialLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        Object initialLevel = Minecraft.getInstance().level;
        long started = System.nanoTime();
        int total = infos.size();
        CompletableFuture<IElementSearch> build = AsyncIngredientFilterBuilder.buildBudgetedAsync(infos,
            visibility, empty, (search, info) -> search.add(info, manager),
            JeiOptFeatureFlags.ingredientFilterChunkSize(), generation, parser);
        CompletableFuture<IElementSearch> sealed = build.thenCompose(search -> sealNativeStorages(search, storages, generation));
        CompletableFuture<Void> published = sealed.thenAccept(search -> {
            if (!Minecraft.getInstance().isSameThread()) {
                throw new IllegalStateException("Filter publication requires client thread");
            }
            JeiOptExecutors.checkJeiStartGeneration(generation);
            if (Minecraft.getInstance().level != initialLevel) {
                throw new java.util.concurrent.CancellationException("World changed during native filter build");
            }
            publish.accept(search);
            if (initialResources != resourceRevision()
                || !initialLanguage.equals(Minecraft.getInstance().getLanguageManager().getSelected())) {
                rebuild.run();
            }
            refresh.run();
            JeiOptStartupProgressState.markPublished(generation);
            JeiOptimize.LOGGER.info("JEI native budgeted filter published: generation={}, ingredients={}, totalMs={}, retainedBuilders={}",
                generation, total, (System.nanoTime() - started) / 1_000_000L, storages.size());
        });
        published.whenComplete((ignored, failure) -> {
            if (failure != null) {
                build.cancel(false);
                sealed.cancel(false);
                Minecraft.getInstance().execute(() -> storages.forEach(DeferredNativeSearchStorage::discard));
            }
        });
        GATE.register(generation, published);
        JeiOptRuntimeState.track(build);
        JeiOptRuntimeState.track(published);
    }

    private static CompletableFuture<IElementSearch> sealNativeStorages(IElementSearch search,
        List<DeferredNativeSearchStorage> storages, long generation) {
        if (storages.isEmpty()) {
            return CompletableFuture.completedFuture(search);
        }
        CompletableFuture<IElementSearch> sealed = new CompletableFuture<>();
        JeiOptRuntimeState.track(sealed);
        int[] cursor = {0};
        long[] totalNanos = {0};
        JeiOptClientTickQueue.enqueue(() -> {
            if (sealed.isDone() || !JeiOptRuntimeState.isCurrent(generation)) {
                storages.forEach(DeferredNativeSearchStorage::discard);
                sealed.cancel(false);
                return true;
            }
            try {
                long started = System.nanoTime();
                storages.get(cursor[0]++).finish();
                totalNanos[0] += System.nanoTime() - started;
                if (cursor[0] < storages.size()) {
                    return false;
                }
                JeiOptimize.LOGGER.info("JEI native builders sealed: generation={}, builders={}, sealMs={}",
                    generation, storages.size(), totalNanos[0] / 1_000_000L);
                sealed.complete(search);
            } catch (RuntimeException | LinkageError failure) {
                storages.forEach(DeferredNativeSearchStorage::discard);
                sealed.completeExceptionally(failure);
            }
            return true;
        });
        return sealed;
    }

    public static void scheduleTooltip(List<? extends IListElementInfo<?>> infos, IIngredientManager manager,
        IIngredientVisibility visibility, ElementPrefixParser parser, IElementSearch empty,
        Supplier<IElementSearch> factory, Supplier<IElementSearch> baseline, Consumer<IElementSearch> publish,
        Runnable rebuild, Runnable refresh) {
        long generation = JeiOptRuntimeState.currentGeneration();
        long initialResources = resourceRevision();
        String initialLanguage = Minecraft.getInstance().getLanguageManager().getSelected();
        CompletableFuture<IElementSearch> build;
        try {
            TooltipSearchAdapter adapter = new TooltipSearchAdapter(parser, empty);
            if (adapter.mode().equals("DISABLED")) {
                throw new IllegalStateException("Tooltip search disabled");
            }
            build = new TooltipFilterBuild(infos, manager, visibility, parser, empty, factory, adapter).start();
        } catch (Exception | LinkageError failure) {
            JeiOptimize.LOGGER.info("JEI tooltip index bypass: {}; retaining native search", failure.getMessage());
            publish.accept(baseline.get());
            refresh.run();
            return;
        } catch (Throwable failure) {
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(failure);
        }
        CompletableFuture<Void> published = build.thenAccept(search -> {
            if (!Minecraft.getInstance().isSameThread()) {
                throw new IllegalStateException("Filter publication requires client thread");
            }
            JeiOptExecutors.checkJeiStartGeneration(generation);
            publish.accept(search);
            if (initialResources != resourceRevision()
                || !initialLanguage.equals(Minecraft.getInstance().getLanguageManager().getSelected())) {
                rebuild.run();
            }
            refresh.run();
            JeiOptStartupProgressState.markReady(generation);
            JeiOptStartupProgressState.markPublished(generation);
            JeiOptimize.LOGGER.info("JEI tooltip filter published on client thread: generation={}", generation);
        });
        GATE.register(generation, published);
        JeiOptRuntimeState.track(published);
    }

    public static void capture(
        List<IListElementInfo<?>> ingredients,
        IIngredientManager ingredientManager,
        IElementSearch emptySearch
    ) {
        capture(ingredients, ingredientManager, emptySearch, List.of());
    }

    public static void capture(List<IListElementInfo<?>> ingredients, IIngredientManager ingredientManager,
        IElementSearch emptySearch, List<DeferredNativeSearchStorage> storages) {
        pending = new Pending(ingredients, ingredientManager, emptySearch, storages);
    }

    public static Pending take() {
        Pending taken = pending;
        pending = null;
        return taken;
    }

    public static void clear() {
        pending = null;
        startupTiming = null;
        GATE.clear();
    }

    private static final class StartupTiming {
        private final long generation;
        private final long started = System.nanoTime();
        private long ticks;
        private long totalTickNanos;
        private long maxTickNanos;
        private volatile boolean published;

        private StartupTiming(long generation) {
            this.generation = generation;
        }
    }

    public record Pending(
        List<IListElementInfo<?>> ingredients,
        IIngredientManager ingredientManager,
        IElementSearch emptySearch,
        List<DeferredNativeSearchStorage> storages
    ) {
    }
}
