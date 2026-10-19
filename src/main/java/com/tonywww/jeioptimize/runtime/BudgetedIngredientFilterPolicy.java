package com.tonywww.jeioptimize.runtime;

/**
 * Decisions for the modern budgeted ingredient filter. The mixin owns the side effects;
 * this class only classifies the outcome so the failure and tooltip paths can be checked
 * without launching Minecraft.
 */
public final class BudgetedIngredientFilterPolicy {
    public enum Publication {
        /** Completed index for the current generation. Safe to install. */
        PUBLISH,
        /** Generation changed or startup was cancelled. Do not install an index. */
        DISCARD_STALE_OR_CANCELLED,
        /** Current generation failed or never finished. Do not install an empty or partial index. */
        ABORT_UNPUBLISHED
    }

    public enum Capture {
        /** Low-memory path. JEI builds the full search directly, with no capture. */
        LOW_MEMORY,
        /** Flags do not defer. JEI's full search is observed, with no capture. */
        FULL_NATIVE,
        /** Empty search plus native storage builders, for the deferred native route. */
        EMPTY_WITH_STORAGES,
        /** Empty search for the tooltip or budgeted-await route. */
        EMPTY
    }

    public enum Schedule {
        NO_CAPTURE,
        TOOLTIP,
        NATIVE,
        BUDGETED_AWAIT,
        /** A captured empty search with no later job. The caller must not treat that as success. */
        CAPTURED_WITHOUT_JOB
    }

    public enum RedirectObservation {
        /** Constructor contract matched, but the createElementSearch redirect did not run. */
        NOT_CONFIRMED,
        /** Redirect ran and left JEI's own search in place. */
        RAN_WITHOUT_BUDGETED_CAPTURE,
        /** Redirect ran and captured an empty search for the budgeted build. */
        BUDGETED_BUILD_SCHEDULED
    }

    private BudgetedIngredientFilterPolicy() {
    }

    public static Publication publication(boolean generationCurrent, boolean startupCancelled, boolean publishable) {
        if (!generationCurrent || startupCancelled) {
            return Publication.DISCARD_STALE_OR_CANCELLED;
        }
        return publishable ? Publication.PUBLISH : Publication.ABORT_UNPUBLISHED;
    }

    /**
     * Same branches as {@code IngredientFilterModernMixin}'s redirect.
     * {@code canDeferFilter} is only true in production when {@code asyncOrDeferred} is true.
     */
    public static Capture captureChoice(
        boolean lowMemory,
        boolean canDeferFilter,
        boolean jeiStartThread,
        boolean asyncOrDeferred,
        boolean canDeferTooltip,
        boolean tooltipMetrics
    ) {
        if (lowMemory) {
            return Capture.LOW_MEMORY;
        }
        if (!canDeferFilter && (!jeiStartThread || !asyncOrDeferred)) {
            return Capture.FULL_NATIVE;
        }
        if (canDeferFilter && !canDeferTooltip && !tooltipMetrics) {
            return Capture.EMPTY_WITH_STORAGES;
        }
        return Capture.EMPTY;
    }

    public static Schedule scheduleChoice(
        boolean sortingConfig,
        boolean captured,
        boolean canDeferTooltip,
        boolean canDeferFilter,
        boolean asyncOrDeferred
    ) {
        if (!captured) {
            return Schedule.NO_CAPTURE;
        }
        if (!sortingConfig && canDeferTooltip) {
            return Schedule.TOOLTIP;
        }
        if (canDeferFilter) {
            return Schedule.NATIVE;
        }
        if (asyncOrDeferred) {
            return Schedule.BUDGETED_AWAIT;
        }
        return Schedule.CAPTURED_WITHOUT_JOB;
    }

    /**
     * JEI 15.62 applies type visibility in the constructor. The tooltip index build sets
     * visibility from {@code isIngredientVisible} alone and can show those elements again.
     * That combination stays on the budgeted path, which keeps constructor-hidden elements hidden.
     */
    public static boolean rejectTooltipSearchIndex(boolean sortingConfigConstructor, boolean tooltipSearchIndexEnabled) {
        return sortingConfigConstructor && tooltipSearchIndexEnabled;
    }

    public static RedirectObservation observe(boolean redirectEntered, boolean capturedBudgetedBuild) {
        if (!redirectEntered) {
            return RedirectObservation.NOT_CONFIRMED;
        }
        if (!capturedBudgetedBuild) {
            return RedirectObservation.RAN_WITHOUT_BUDGETED_CAPTURE;
        }
        return RedirectObservation.BUDGETED_BUILD_SCHEDULED;
    }

    public static boolean publishable(boolean done, boolean cancelled, boolean completedExceptionally) {
        return done && !cancelled && !completedExceptionally;
    }
}
