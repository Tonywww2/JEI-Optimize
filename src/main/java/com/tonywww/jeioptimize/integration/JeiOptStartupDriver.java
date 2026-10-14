package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncIngredientFilterBuilder;
import com.tonywww.jeioptimize.index.TooltipSearchShadow;
import com.tonywww.jeioptimize.index.TooltipFilterBuild;
import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.recipe.JeiRecipeGenerationLimiter;
import com.tonywww.jeioptimize.runtime.ForgeGuiRegistrationTracker;
import com.tonywww.jeioptimize.runtime.JeiOptClientTickQueue;
import com.tonywww.jeioptimize.runtime.JeiOptCacheScope;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptFilterBootstrap;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import net.minecraft.client.Minecraft;

/** Coordinates generation cleanup and runtime-finalization diagnostics around JEI startup. */
public final class JeiOptStartupDriver {
    private JeiOptStartupDriver() {
    }

    public static void onJeiStarting() {
        clearRuntimeWork();
        com.tonywww.jeioptimize.instrumentation.JeiMemoryDiagnostics.begin(JeiOptRuntimeState.currentGeneration());
        JeiOptFilterBootstrap.begin(JeiOptRuntimeState.currentGeneration());
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
        com.tonywww.jeioptimize.instrumentation.JeiMemoryDiagnostics.finish("stopped");
        long generation = JeiOptRuntimeState.currentGeneration();
        boolean cancelledStartup = JeiOptExecutors.cancelJeiStart();
        try {
            JeiOptStartupProgressState.cancel(generation);
            JeiOptRuntimeState.invalidate();
            JeiOptRuntimeState.markRuntimeUnloaded();
            cleanupUnpublishedForgeGui(generation);
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

    private static void cleanupUnpublishedForgeGui(long generation) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.isSameThread()) {
            ForgeGuiRegistrationTracker.cleanupIfUnpublished(generation);
            return;
        }
        minecraft.execute(() -> ForgeGuiRegistrationTracker.cleanupIfUnpublished(generation));
    }

    private static void clearRuntimeWork() {
        AsyncIngredientFilterBuilder.cancelInFlight();
        TooltipFilterBuild.cancelAll();
        TooltipSearchShadow.cancelAll();
        JeiRecipeGenerationLimiter.clearAll();
        JeiOptCacheScope.clear();
        CelestialForgeReinforceCache.clear();
        CelestialForgeReinforceInputPool.clear();
        EmbersDawnstoneAnvilCompactor.clear();
        IronFurnacesGeneratorCompactor.clear();
        IronsSpellsRecipeCompactor.clear();
        SfmFallingAnvilCache.clear();
        SfmFallingAnvilRepresentativeLimiter.clear();
        UltimateCarWorkshopCompactor.clear();
        JeiOptFilterBootstrap.clear();
        JeiOptClientTickQueue.clear();
        com.tonywww.jeioptimize.runtime.CategoryVisibilityHost.clear();
        JeiOptExecutors.shutdownWorkerExecutor();
    }
}
