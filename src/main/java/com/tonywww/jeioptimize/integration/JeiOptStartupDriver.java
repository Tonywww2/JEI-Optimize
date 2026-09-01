package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptCacheScope;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;

/** Coordinates generation cleanup and runtime-finalization diagnostics around JEI startup. */
public final class JeiOptStartupDriver {
    private JeiOptStartupDriver() {
    }

    public static void onJeiStarting() {
        clearRuntimeWork();
    }

    public static void onRuntimeAvailable() {
        if (!JeiOptFeatureFlags.enabled()) {
            return;
        }
        try {
            JeiOptDiagnostics.reportRegistrationCounts();
            int workerThreads = JeiOptFeatureFlags.workerThreads();
            JeiOptExecutors.configureWorkerThreads(workerThreads);
            JeiOptimize.LOGGER.info(
                "JEI Optimize worker configuration: {} threads, parallel threshold {}",
                workerThreads,
                JeiOptFeatureFlags.parallelThreshold()
            );
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI Optimize runtime finalization failed; JEI baseline remains active", e);
        }
    }

    public static void onRuntimeUnavailable() {
        onJeiStopping();
    }

    public static void onJeiStopping() {
        long generation = JeiOptRuntimeState.currentGeneration();
        boolean cancelledStartup = JeiOptExecutors.cancelJeiStart();
        try {
            JeiOptStartupProgressState.cancel(generation);
            JeiOptRuntimeState.invalidate();
            JeiOptRuntimeState.markRuntimeUnloaded();
            clearRuntimeWork();
            if (cancelledStartup) {
                JeiOptimize.LOGGER.info(
                    "JEI Optimize cancelled the in-progress JEI startup because the client disconnected or its world stopped."
                );
            }
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI Optimize failed to tear down runtime state", e);
        }
    }

    private static void clearRuntimeWork() {
        AsyncIngredientFilterBuilder.cancelInFlight();
        JeiOptCacheScope.clear();
        CelestialForgeReinforceCache.clear();
        CelestialForgeReinforceInputPool.clear();
        EmbersDawnstoneAnvilCompactor.clear();
        IronFurnacesGeneratorCompactor.clear();
        IronsSpellsRecipeCompactor.clear();
        SfmFallingAnvilCache.clear();
        UltimateCarWorkshopCompactor.clear();
        JeiOptFilterBootstrap.clear();
        JeiOptClientTickQueue.clear();
        JeiOptExecutors.shutdownWorkerExecutor();
    }
}
