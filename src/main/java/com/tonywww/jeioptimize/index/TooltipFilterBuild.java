package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import com.tonywww.jeioptimize.runtime.JeiOptTooltipCache;
import com.tonywww.jeioptimize.runtime.TooltipCaptureContext;
import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IIngredientVisibility;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;

public final class TooltipFilterBuild {
    private static final Set<TooltipFilterBuild> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private final long generation = JeiOptRuntimeState.currentGeneration();
    private List<? extends IListElementInfo<?>> infos;
    private List<IListElement<?>> elements;
    private IIngredientManager manager;
    private IIngredientVisibility visibility;
    private ElementPrefixParser parser;
    private Supplier<IElementSearch> nativeFactory;
    private TooltipSearchAdapter adapter;
    private String mode;
    private Object level = Minecraft.getInstance().level;
    private Object player = Minecraft.getInstance().player;
    private String language = Minecraft.getInstance().getLanguageManager().getSelected();
    private long resourceRevision = com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap.resourceRevision();
    private final JeiOptTooltipCache cache;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CompletableFuture<IElementSearch> completion = new CompletableFuture<>();
    private final List<String> tokens = new ArrayList<>(List.of("", "attack", "\u0000absent\u0000", " "));
    private TooltipSearchBackend backend;
    private IElementSearch nativeSearch;
    private IElementSearch reference;
    private TooltipAwareElementSearch candidate;
    private TooltipIndexPipeline pipeline;
    private TooltipSnapshotProducer producer;
    private int referenceCursor;
    private int queryCursor;
    private int extracted;
    private int ticks;
    private long extractionNanos;
    private long maxCallbackNanos;
    private long maxTickNanos;
    private long stepStarted;
    private long referenceAddNanos;
    private long getterNanos;
    private long maxGetterNanos;
    private int getterCalls;
    private int backpressureYields;
    private int waitingTicks;
    private int maxElementsPerTick;
    private int fallbackAttempts;
    private boolean referenceStarted;
    private boolean fallback;
    private boolean validating;
    private int tooltipSettings = -1;
    private BooleanSupplier advancedSetting;
    private final long started = System.nanoTime();

    public TooltipFilterBuild(List<? extends IListElementInfo<?>> infos, IIngredientManager manager,
        IIngredientVisibility visibility, ElementPrefixParser parser, IElementSearch nativeSearch,
        Supplier<IElementSearch> nativeFactory, TooltipSearchAdapter adapter) throws ReflectiveOperationException {
        this.infos = List.copyOf(infos);
        this.elements = infos.stream().map(IListElementInfo::getElement).<IListElement<?>>map(element -> element).toList();
        this.manager = manager;
        this.visibility = visibility;
        this.parser = parser;
        this.nativeSearch = nativeSearch;
        this.nativeFactory = nativeFactory;
        this.adapter = adapter;
        this.mode = adapter.mode();
        this.backend = new TooltipSearchBackend(adapter.modern());
        this.cache = new JeiOptTooltipCache(JeiOptFeatureFlags.tooltipStringCache() ? 16L * 1024 * 1024 : 0);
    }

    public CompletableFuture<IElementSearch> start() {
        JeiOptExecutors.configureWorkerThreads(JeiOptFeatureFlags.workerThreads());
        TooltipSearchBackend target = backend;
        pipeline = new TooltipIndexPipeline(JeiOptExecutors.workerExecutor(), 2, 3L * 1024 * 1024,
            adapter.modern(), target::put);
        producer = new TooltipSnapshotProducer(pipeline, infos.size());
        synchronized (ACTIVE) {
            ACTIVE.add(this);
        }
        completion.whenComplete((result, failure) -> {
            if (failure != null) {
                cancelled.set(true);
                pipeline.cancel();
                Minecraft.getInstance().execute(this::release);
            }
        });
        JeiOptRuntimeState.track(completion);
        JeiOptStartupProgressState.registerBuild(generation, infos.size(), infos.size());
        JeiOptClientTickQueue.enqueue(this::step);
        return completion;
    }

    private boolean step() {
        if (cancelled.get() || !JeiOptRuntimeState.isCurrent(generation)) {
            cancel();
            return true;
        }
        long tickStart = System.nanoTime();
        stepStarted = tickStart;
        ticks++;
        try {
            if (!Minecraft.getInstance().isSameThread()) {
                throw new IllegalStateException("Tooltip extraction requires client thread");
            }
            if (Minecraft.getInstance().level != level) {
                cancel();
                return true;
            }
            if (!fallback && pipeline.failure() != null) {
                beginFallback(pipeline.failure());
            }
            if (!Minecraft.getInstance().getLanguageManager().getSelected().equals(language)
                || resourceRevision != com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap.resourceRevision()) {
                language = Minecraft.getInstance().getLanguageManager().getSelected();
                resourceRevision = com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap.resourceRevision();
                cache.clear();
                beginFallback(new IllegalStateException("Tooltip resources changed during build"));
            }
            if (Minecraft.getInstance().player != player) {
                player = Minecraft.getInstance().player;
                cache.clear();
                beginFallback(new IllegalStateException("Tooltip player context changed during build"));
            }
            if (!mode.equals(adapter.mode())) {
                mode = adapter.mode();
                cache.clear();
                beginFallback(new IllegalStateException("Tooltip mode changed during build"));
            }
            if (advancedSetting != null && (advancedSetting.getAsBoolean() ? 1 : 0) != tooltipSettings) {
                cache.clear();
                tooltipSettings = advancedSetting.getAsBoolean() ? 1 : 0;
                beginFallback(new IllegalStateException("Advanced tooltip settings changed during build"));
            }
            if (fallback) {
                if (!pipeline.completion().isDone()) {
                    waitingTicks++;
                    return false;
                }
                if (!referenceStarted) {
                    reference = nativeFactory.get();
                    referenceStarted = true;
                }
                return buildReference(true);
            }
            if (validating) {
                return buildReference(false);
            }
            TooltipSnapshotProducer.Step progress = producer.pump(JeiOptClientTickQueue::hasTimeRemaining, this::extract);
            maxElementsPerTick = Math.max(maxElementsPerTick, progress.processed());
            if (progress.backpressured()) {
                backpressureYields++;
            }
            if (!producer.finished() || !pipeline.completion().isDone()) {
                if (progress.processed() == 0) {
                    waitingTicks++;
                }
                return false;
            }
            pipeline.completion().join();
            if (JeiOptFeatureFlags.tooltipSearchMetrics()) {
                validating = true;
                reference = nativeFactory.get();
                return false;
            }
            complete(new TooltipAwareElementSearch(nativeSearch, adapter, elements, backend));
            return true;
        } catch (RuntimeException | LinkageError failure) {
            if (fallback) {
                completion.completeExceptionally(failure);
                release();
                return true;
            }
            try {
                beginFallback(failure);
            } catch (RuntimeException | LinkageError fallbackFailure) {
                completion.completeExceptionally(fallbackFailure);
                release();
                return true;
            }
            return false;
        } finally {
            maxTickNanos = Math.max(maxTickNanos, System.nanoTime() - tickStart);
        }
    }

    private TooltipSearchSnapshot extract(int ordinal) {
        if (cancelled.get() || !JeiOptRuntimeState.isCurrent(generation)) {
            throw new java.util.concurrent.CancellationException();
        }
        IListElementInfo<?> info = infos.get(ordinal);
        info.getElement().setVisible(visibility.isIngredientVisible(info.getTypedIngredient()));
        TooltipCaptureContext capture = TooltipCaptureContext.suppress(info, ordinal);
        addMeasured(nativeSearch, info, capture, false);
        if (capture.failed() || capture.snapshot() == null) {
            throw new IllegalStateException("Incomplete tooltip capture");
        }
        if (tooltipSettings != -1 && tooltipSettings != capture.settings()) {
            cache.clear();
            throw new IllegalStateException("Tooltip settings changed during build");
        }
        tooltipSettings = capture.settings();
        advancedSetting = capture.advancedSetting();
        TooltipSearchSnapshot snapshot = capture.snapshot();
        extracted++;
        cache.put(ordinal, snapshot.tooltipStrings());
        if (JeiOptFeatureFlags.tooltipSearchMetrics()) {
            for (String text : snapshot.tooltipStrings()) {
                if (tokens.size() < 64 && !text.isEmpty()) {
                    tokens.add(text);
                    tokens.add(text.substring(0, Math.min(2, text.length())));
                }
            }
        }
        JeiOptStartupProgressState.markChunkCompleted(generation);
        return snapshot;
    }

    private void addMeasured(IElementSearch search, IListElementInfo<?> info, TooltipCaptureContext capture,
        boolean referenceBuild) {
        try (capture) {
            long before = System.nanoTime();
            try {
                search.add(info, manager);
            } finally {
                long elapsed = System.nanoTime() - before;
                extractionNanos += elapsed;
                maxCallbackNanos = Math.max(maxCallbackNanos, elapsed);
                if (referenceBuild) {
                    referenceAddNanos += elapsed;
                }
            }
        } finally {
            getterNanos += capture.getterNanos();
            maxGetterNanos = Math.max(maxGetterNanos, capture.maxGetterNanos());
            getterCalls += capture.getterCalls();
        }
    }

    private boolean buildReference(boolean fallbackBuild) {
        boolean processed = false;
        while (referenceCursor < infos.size() && (!processed || JeiOptClientTickQueue.hasTimeRemaining())) {
            if (cancelled.get() || !JeiOptRuntimeState.isCurrent(generation)) {
                cancel();
                return true;
            }
            IListElementInfo<?> info = infos.get(referenceCursor);
            info.getElement().setVisible(visibility.isIngredientVisible(info.getTypedIngredient()));
            addMeasured(reference, info, TooltipCaptureContext.replay(info, referenceCursor, cache.get(referenceCursor)), true);
            referenceCursor++;
            processed = true;
        }
        if (referenceCursor < infos.size()) {
            return false;
        }
        if (fallbackBuild) {
            complete(reference);
            return true;
        }
        if (candidate == null) {
            candidate = new TooltipAwareElementSearch(nativeSearch, adapter, elements, backend);
        }
        String token = tokens.get(queryCursor);
        for (String query : List.of(token, com.tonywww.jeioptimize.runtime.JeiOptCompatibilityState.tooltipPrefix() + token)) {
            ElementPrefixParser.TokenInfo parsed = parser.parseToken(query).orElse(null);
            if (parsed != null) {
                var expected = reference.getSearchResults(parsed);
                var actual = candidate.getSearchResults(parsed);
                if (actual.size() != expected.size() || !actual.containsAll(expected)) {
                    throw new IllegalStateException("Tooltip query differs at sample " + queryCursor);
                }
            }
        }
        queryCursor++;
        if (queryCursor < tokens.size()) {
            return false;
        }
        validateRuntimeAddition();
        JeiOptimize.LOGGER.info("JEI tooltip optimized validation passed: {} queries, {} ingredients", queryCursor * 2, infos.size());
        complete(candidate);
        return true;
    }

    private void validateRuntimeAddition() {
        if (infos.isEmpty()) {
            return;
        }
        try {
            IElementSearch addedOnly = nativeFactory.get();
            TooltipAwareElementSearch addedWrapper = new TooltipAwareElementSearch(addedOnly, adapter,
                List.of(), new TooltipSearchBackend(adapter.modern()));
            IListElementInfo<?> info = infos.get(0);
            addMeasured(addedWrapper, info, TooltipCaptureContext.replay(info, 0, cache.get(0)), true);
            if (!addedWrapper.getAllIngredients().contains(info.getElement())) {
                throw new IllegalStateException("Runtime addition missing");
            }
            for (String text : tokens) {
                var token = parser.parseToken(com.tonywww.jeioptimize.runtime.JeiOptCompatibilityState.tooltipPrefix() + text);
                if (token.isPresent() && !addedWrapper.getSearchResults(token.get()).equals(addedOnly.getSearchResults(token.get()))) {
                    throw new IllegalStateException("Runtime addition tooltip mismatch");
                }
            }
            JeiOptimize.LOGGER.info("JEI tooltip runtime-add validation passed on isolated delegate");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void beginFallback(Throwable failure) {
        if (++fallbackAttempts > 3) {
            throw new IllegalStateException("Tooltip context changed repeatedly during fallback", failure);
        }
        JeiOptimize.LOGGER.warn("JEI tooltip indexing fell back to native search for generation {}", generation, failure);
        fallback = true;
        validating = false;
        pipeline.cancel();
        producer.clear();
        backend = null;
        nativeSearch = null;
        candidate = null;
        reference = null;
        referenceStarted = false;
        referenceCursor = 0;
    }

    private void complete(IElementSearch search) {
        if (cancelled.get() || !JeiOptRuntimeState.isCurrent(generation)) {
            cancel();
            return;
        }
        TooltipIndexPipeline.Metrics metrics = pipeline.metrics();
        int ingredientCount = infos.size();
        long cacheHits = cache.hits();
        long cacheBytes = cache.weight();
        long peakCharacters = Math.max(producer.peakCharacters(), metrics.peakCharacters());
        long peakBytes = Math.max(producer.peakBytes(), metrics.peakBytes());
        release();
        completion.complete(search);
        maxTickNanos = Math.max(maxTickNanos, System.nanoTime() - stepStarted);
        JeiOptimize.LOGGER.info(
            "JEI tooltip index ready: generation={}, ingredients={}, extracted={}, ticks={}, nativeFallback={}, cacheHits={}, cacheBytes={}, clientAddMs={}, maxElementMs={}, maxBuildStepMs={}, totalMs={}, getterCalls={}, getterMs={}, maxGetterMs={}, workerMs={}, batches={}, consumedBatches={}, peakQueuedBatches={}, peakPendingChars={}, peakPendingBytes={}, queueFullYields={}, waitingTicks={}, maxElementsPerTick={}, referenceAddMs={}",
            generation, ingredientCount, extracted, ticks, fallback, cacheHits, cacheBytes,
            extractionNanos / 1_000_000L, maxCallbackNanos / 1_000_000L, maxTickNanos / 1_000_000L,
            (System.nanoTime() - started) / 1_000_000L,
            getterCalls, getterNanos / 1_000_000L, maxGetterNanos / 1_000_000L,
            metrics.workerNanos() / 1_000_000L, metrics.submitted(), metrics.consumed(), metrics.peakQueued(),
            peakCharacters, peakBytes,
            backpressureYields, waitingTicks, maxElementsPerTick, referenceAddNanos / 1_000_000L
        );
    }

    private void cancel() {
        cancelled.set(true);
        pipeline.cancel();
        completion.cancel(false);
        Minecraft.getInstance().execute(this::release);
    }

    public static void cancelAll() {
        List<TooltipFilterBuild> builds;
        synchronized (ACTIVE) {
            builds = List.copyOf(ACTIVE);
        }
        builds.forEach(TooltipFilterBuild::cancel);
    }

    private void release() {
        synchronized (ACTIVE) {
            ACTIVE.remove(this);
        }
        cache.clear();
        producer.clear();
        infos = List.of();
        elements = List.of();
        manager = null;
        visibility = null;
        parser = null;
        nativeFactory = null;
        adapter = null;
        level = null;
        player = null;
        advancedSetting = null;
        tokens.clear();
        reference = null;
        nativeSearch = null;
        candidate = null;
        backend = null;
    }
}