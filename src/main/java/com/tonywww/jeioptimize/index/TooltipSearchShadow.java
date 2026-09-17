package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptCompatibilityState;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.TooltipCaptureContext;
import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public final class TooltipSearchShadow {
    private static final int MAX_ELEMENTS = 4096;
    private static final int MAX_STRINGS = 16384;
    private static final long MAX_CHARACTERS = 1_000_000;
    private static final int CHUNK_SIZE = 128;
    private static final Set<TooltipSearchShadow> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    private final long generation;
    private final ElementPrefixParser parser;
    private final char prefix;
    private final boolean trimStrings;
    private final int maxPending;
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final List<IListElement<?>> elements = new ArrayList<>();
    private final List<TooltipSearchSnapshot> chunk = new ArrayList<>();
    private final List<CompletableFuture<TooltipSearchIndex>> pending = new ArrayList<>();
    private final List<TooltipSearchIndex> segments = new ArrayList<>();
    private CompletableFuture<Validation> validation;
    private long characters;
    private int strings;
    private int captured;
    private int empty;
    private int compared;
    private long captureNanos;
    private boolean disabled;

    private TooltipSearchShadow(long generation, ElementPrefixParser parser) {
        this.generation = generation;
        this.parser = parser;
        this.prefix = JeiOptCompatibilityState.tooltipPrefix();
        this.trimStrings = JeiOptCompatibilityState.trimTooltipStrings();
        this.maxPending = Math.max(2, JeiOptFeatureFlags.workerThreads() * 2);
    }

    public static TooltipSearchShadow create(long generation, ElementPrefixParser parser, int count) {
        if (!JeiOptFeatureFlags.tooltipSearchMetrics()) {
            return null;
        }
        if (parser == null || JeiOptCompatibilityState.tooltipPrefix() == 0 || count > MAX_ELEMENTS) {
            JeiOptimize.LOGGER.info("JEI tooltip shadow skipped: unsupported ABI or ingredient limit ({})", count);
            return null;
        }
        JeiOptExecutors.configureWorkerThreads(JeiOptFeatureFlags.workerThreads());
        TooltipSearchShadow shadow = new TooltipSearchShadow(generation, parser);
        synchronized (ACTIVE) {
            ACTIVE.add(shadow);
        }
        return shadow;
    }

    public static IElementSearch observeNativeBuild(
        List<? extends IListElementInfo<?>> infos,
        ElementPrefixParser parser,
        Supplier<IElementSearch> build
    ) {
        TooltipSearchShadow shadow = create(JeiOptRuntimeState.currentGeneration(), parser, infos.size());
        if (shadow == null) {
            return build.get();
        }
        if (!Minecraft.getInstance().isSameThread()) {
            shadow.disable("native build outside client thread", null);
            return build.get();
        }
        Map<Object, Integer> ordinals = new IdentityHashMap<>();
        for (int ordinal = 0; ordinal < infos.size(); ordinal++) {
            ordinals.put(infos.get(ordinal), ordinal);
            shadow.elements.add(infos.get(ordinal).getElement());
        }
        BitSet seen = new BitSet();
        IElementSearch[] result = new IElementSearch[1];
        long started = System.nanoTime();
        try {
            TooltipCaptureContext.observe((element, strings) -> {
                Integer ordinal = ordinals.get(element);
                if (ordinal == null || shadow.disabled) {
                    return;
                }
                try {
                    if (seen.get(ordinal)) {
                        shadow.disable("duplicate native capture", null);
                        return;
                    }
                    seen.set(ordinal);
                    shadow.accept(new TooltipSearchSnapshot(ordinal, List.copyOf(strings)));
                } catch (RuntimeException | LinkageError failure) {
                    shadow.disable("native capture failed", failure);
                }
            }, () -> result[0] = build.get());
        } catch (RuntimeException | Error failure) {
            shadow.cancel();
            throw failure;
        }
        shadow.captureNanos = System.nanoTime() - started;
        if (!shadow.disabled) {
            JeiOptClientTickQueue.enqueue(() -> shadow.finish(result[0]));
        }
        return result[0];
    }

    public static void cancelAll() {
        List<TooltipSearchShadow> active;
        synchronized (ACTIVE) {
            active = List.copyOf(ACTIVE);
        }
        active.forEach(TooltipSearchShadow::cancel);
    }

    public boolean canCapture() {
        if (disabled) {
            return true;
        }
        try {
            for (int index = pending.size() - 1; index >= 0; index--) {
                CompletableFuture<TooltipSearchIndex> future = pending.get(index);
                if (future.isDone()) {
                    segments.add(future.join());
                    pending.remove(index);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disable("worker failure", failure);
        }
        return disabled || pending.size() < maxPending;
    }

    public void add(
        IElementSearch search,
        IListElementInfo<?> info,
        AsyncIngredientFilterBuilder.ElementAppender appender
    ) {
        if (disabled) {
            appender.add(search, info);
            return;
        }
        if (!Minecraft.getInstance().isSameThread()) {
            disable("capture outside client thread", null);
            throw new IllegalStateException("Ingredient extraction requires the client thread");
        }
        int ordinal = elements.size();
        elements.add(info.getElement());
        long started = System.nanoTime();
        try (TooltipCaptureContext capture = TooltipCaptureContext.open(info, ordinal)) {
            appender.add(search, info);
            captureNanos += System.nanoTime() - started;
            if (capture.failed()) {
                disable("invalid capture", null);
            } else if (capture.snapshot() != null) {
                accept(capture.snapshot());
            }
        }
    }

    private void accept(TooltipSearchSnapshot snapshot) {
        captured++;
        if (snapshot.tooltipStrings().isEmpty()) {
            empty++;
        }
        strings += snapshot.tooltipStrings().size();
        for (String text : snapshot.tooltipStrings()) {
            characters += text.length();
        }
        if (strings > MAX_STRINGS || characters > MAX_CHARACTERS) {
            disable("snapshot memory limit", null);
            return;
        }
        chunk.add(snapshot);
        if (chunk.size() == CHUNK_SIZE) {
            submitChunk();
        }
    }

    private void submitChunk() {
        if (chunk.isEmpty()) {
            return;
        }
        canCapture();
        if (disabled) {
            return;
        }
        if (pending.size() >= maxPending) {
            disable("diagnostic worker queue full", null);
            return;
        }
        List<TooltipSearchSnapshot> batch = List.copyOf(chunk);
        boolean trim = trimStrings;
        AtomicBoolean cancellation = cancelled;
        try {
            CompletableFuture<TooltipSearchIndex> future = JeiOptExecutors.supplyAsync(
                () -> TooltipSearchIndex.build(batch, trim, cancellation::get)
            );
            JeiOptRuntimeState.track(future);
            pending.add(future);
            chunk.clear();
        } catch (RuntimeException | LinkageError failure) {
            disable("worker submission failed", failure);
        }
    }

    public boolean finish(IElementSearch search) {
        if (disabled) {
            return true;
        }
        if (!JeiOptRuntimeState.isCurrent(generation) || !JeiOptFeatureFlags.tooltipSearchMetrics()) {
            cancel();
            return true;
        }
        try {
            ElementPrefixParser.TokenInfo probe = parser.parseToken(prefix + "jet-tooltip-probe").orElse(null);
            boolean enabled = probe != null && probe.token().equals("jet-tooltip-probe");
            if (!enabled || captured != elements.size()) {
                disable(!enabled ? "tooltip search disabled" : "incomplete capture", null);
                return true;
            }
            submitChunk();
            canCapture();
            if (disabled) {
                return true;
            }
            if (!pending.isEmpty()) {
                return false;
            }
            if (validation == null) {
                List<TooltipSearchIndex> ready = List.copyOf(segments);
                AtomicBoolean cancellation = cancelled;
                validation = JeiOptExecutors.supplyAsync(() -> validate(ready, cancellation));
                JeiOptRuntimeState.track(validation);
                segments.clear();
                return false;
            }
            if (!validation.isDone()) {
                return false;
            }
            Validation result = validation.join();
            String token = result.tokens().get(compared);
            Set<IListElement<?>> expected = Collections.newSetFromMap(new IdentityHashMap<>());
            BitSet ordinals = result.results().get(compared);
            for (int ordinal = ordinals.nextSetBit(0); ordinal >= 0; ordinal = ordinals.nextSetBit(ordinal + 1)) {
                expected.add(elements.get(ordinal));
            }
            Set<IListElement<?>> actual = parser.parseToken(prefix + token)
                .map(search::getSearchResults).orElseGet(Set::of);
            if (actual.size() != expected.size() || !expected.containsAll(actual)) {
                disable("native query mismatch at sample " + compared, null);
                return true;
            }
            compared++;
            if (compared < result.tokens().size()) {
                return false;
            }
            JeiOptimize.LOGGER.info(
                "JEI tooltip shadow passed: generation={}, captured={}, empty={}, strings={}, queries={}, clientAddMs={}, mergeQueryMs={}; native search retained",
                generation, captured, empty, result.stringCount(), compared,
                captureNanos / 1_000_000L, result.workerNanos() / 1_000_000L
            );
            cancel();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disable("validation failed", failure);
            return true;
        }
    }

    private static Validation validate(List<TooltipSearchIndex> segments, AtomicBoolean cancelled) {
        long started = System.nanoTime();
        TooltipSearchIndex index = TooltipSearchIndex.merge(segments, cancelled::get);
        List<String> tokens = index.validationTokens(64);
        List<BitSet> results = tokens.stream().map(token -> index.search(token, cancelled::get)).toList();
        return new Validation(tokens, results, index.stringCount(), System.nanoTime() - started);
    }

    private void disable(String reason, Throwable failure) {
        JeiOptimize.LOGGER.warn("JEI tooltip shadow stopped: {}; native search retained", reason, failure);
        cancel();
    }

    public void cancel() {
        cancelled.set(true);
        synchronized (ACTIVE) {
            ACTIVE.remove(this);
        }
        disabled = true;
        pending.forEach(future -> future.cancel(true));
        if (validation != null) {
            validation.cancel(true);
            validation = null;
        }
        pending.clear();
        segments.clear();
        chunk.clear();
        elements.clear();
    }

    private record Validation(List<String> tokens, List<BitSet> results, int stringCount, long workerNanos) {}
}