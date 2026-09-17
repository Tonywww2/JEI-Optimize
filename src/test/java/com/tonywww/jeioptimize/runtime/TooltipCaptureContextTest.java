package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.snapshot.TooltipSearchSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class TooltipCaptureContextTest {
    public static void main(String[] arguments) throws Exception {
        Object element = new Object();
        java.util.concurrent.atomic.AtomicLong clock = new java.util.concurrent.atomic.AtomicLong(100);
        TooltipCaptureContext timed = new TooltipCaptureContext(element, 0, clock::get);
        try (timed) {
            TooltipCaptureContext.beginGetter(element);
            clock.addAndGet(12);
            TooltipCaptureContext.endGetter(element);
            clock.addAndGet(99);
            check(timed.getterNanos() == 12 && timed.getterCalls() == 1, "getter excludes add tail");
            TooltipCaptureContext.beginGetter(element);
            clock.addAndGet(18);
        }
        check(timed.getterNanos() == 30 && timed.maxGetterNanos() == 18 && timed.getterCalls() == 2,
            "scope exit accounts for throwing getter");
        try (TooltipCaptureContext replay = TooltipCaptureContext.replay(element, 0, List.of())) {
            TooltipCaptureContext.beginGetter(element);
            TooltipCaptureContext.endGetter(element);
            check(replay.getterCalls() == 0, "cache replay is not a getter call");
        }
        ArrayList<String> strings = new ArrayList<>(List.of("attack", ""));
        try (TooltipCaptureContext capture = TooltipCaptureContext.open(element, 7)) {
            TooltipCaptureContext.record(new Object(), List.of("unrelated"));
            check(capture.snapshot() == null, "unrelated getter must not be captured");
            Thread worker = new Thread(() -> TooltipCaptureContext.record(element, List.of("worker")));
            worker.start();
            worker.join();
            check(capture.snapshot() == null, "capture must be thread-local");
            TooltipCaptureContext.record(element, strings);
            strings.clear();
            check(capture.snapshot().elementOrdinal() == 7, "ordinal");
            check(capture.snapshot().tooltipStrings().equals(List.of("attack", "")), "defensive copy");
            expect(UnsupportedOperationException.class, () -> capture.snapshot().tooltipStrings().clear());
            TooltipCaptureContext.record(element, List.of());
            check(capture.failed(), "duplicate capture fails only the diagnostic");
        }
        try (TooltipCaptureContext outer = TooltipCaptureContext.open(element, 1)) {
            expect(IllegalArgumentException.class, () -> {
                try (TooltipCaptureContext inner = TooltipCaptureContext.open(element, 2)) {
                    TooltipCaptureContext.record(element, List.of());
                    check(inner.snapshot() != null && inner.snapshot().tooltipStrings().isEmpty(), "empty is success");
                    throw new IllegalArgumentException("test");
                }
            });
            TooltipCaptureContext.record(element, List.of("outer"));
            check(outer.snapshot().tooltipStrings().equals(List.of("outer")), "restore after exception");
        }
        TooltipCaptureContext.record(element, null);
        List<String> observed = new ArrayList<>();
        TooltipCaptureContext.observe((source, values) -> observed.addAll(values), () -> {
            TooltipCaptureContext.record(element, List.of("before"));
            expect(IllegalArgumentException.class, () -> TooltipCaptureContext.observe((source, values) -> {}, () -> {
                throw new IllegalArgumentException("test");
            }));
            TooltipCaptureContext.record(element, List.of("after"));
        });
        check(observed.equals(List.of("before", "after")), "observer restored after exception");
        check(!TooltipCaptureContext.isCapturing(element), "observer removed");
        try (TooltipCaptureContext suppression = TooltipCaptureContext.suppress(element, 0)) {
            check(TooltipCaptureContext.suppresses(element), "isolated build suppression");
            check(!TooltipCaptureContext.suppresses(new Object()), "unrelated tooltip unaffected");
            TooltipCaptureContext.settings(element, true);
            check(suppression.settings() == 1, "advanced tooltip fingerprint");
            try (TooltipCaptureContext replay = TooltipCaptureContext.replay(element, 0, List.of())) {
                check(!TooltipCaptureContext.suppresses(element), "replay is native insertion");
                check(TooltipCaptureContext.replayStrings(element).isEmpty(), "empty replay hit");
            }
            check(TooltipCaptureContext.suppresses(element), "restore suppression");
            TooltipCaptureContext.fail(element);
            check(!TooltipCaptureContext.suppresses(element), "failed capture cannot suppress");
        }
        check(!TooltipCaptureContext.suppresses(element), "suppression removed");
        expect(IllegalArgumentException.class, () -> new TooltipSearchSnapshot(-1, List.of()));
        expect(NullPointerException.class, () -> new TooltipSearchSnapshot(0, null));
        System.out.println("TooltipCaptureContextTest passed");
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable failure) {
            check(type.isInstance(failure), "unexpected exception: " + failure);
            return;
        }
        throw new AssertionError("Expected " + type.getName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}