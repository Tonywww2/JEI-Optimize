package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.index.AsyncSearchIndex;
import com.tonywww.jeioptimize.index.AsyncSearchIndexRegistry;
import com.tonywww.jeioptimize.instrumentation.JeiOptDiagnostics;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupContext;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.ingredients.IListElementInfo;

import java.util.List;

/**
 * Drives project-owned async preheating after JEI startup completes.
 *
 * <p>All work here runs after JEI reports its runtime is available, so it never counts against
 * JEI's startup timing. Every step is guarded: any failure leaves JEI's baseline behavior intact.
 */
public final class JeiOptStartupDriver {
    private JeiOptStartupDriver() {
    }

    public static void onRuntimeAvailable() {
        if (!JeiOptFeatureFlags.enabled()) {
            return;
        }
        try {
            JeiOptDiagnostics.reportRegistrationCounts();
            JeiOptExecutors.configureWorkerThreads(JeiOptFeatureFlags.workerThreads());
            JeiOptRuntimeState.beginStart();
            driveSearchPreheat();
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI Optimize preheat wiring failed; JEI baseline remains active", e);
        }
    }

    private static void driveSearchPreheat() {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return;
        }

        Object elementSearch = JeiOptStartupContext.elementSearch();
        List<? extends IListElementInfo<?>> ingredientInfos = JeiOptStartupContext.ingredientInfos();
        IIngredientManager ingredientManager = JeiOptStartupContext.ingredientManager();
        IIngredientFilterConfig ingredientFilterConfig = JeiOptStartupContext.ingredientFilterConfig();
        IColorHelper colorHelper = JeiOptStartupContext.colorHelper();

        if (elementSearch == null
            || ingredientInfos == null
            || ingredientInfos.isEmpty()
            || ingredientManager == null
            || ingredientFilterConfig == null
            || colorHelper == null) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize search preheat skipped (incomplete context): elementSearch={}, infos={}, ingredientManager={}, filterConfig={}, colorHelper={}",
                elementSearch != null,
                ingredientInfos == null ? -1 : ingredientInfos.size(),
                ingredientManager != null,
                ingredientFilterConfig != null,
                colorHelper != null
            );
            return;
        }

        AsyncSearchIndex.buildAsyncFromElementInfos(ingredientInfos, ingredientManager, ingredientFilterConfig, colorHelper)
            .ifPresent(index -> {
                AsyncSearchIndexRegistry.attach(elementSearch, index);
                JeiOptimize.LOGGER.info("JEI Optimize search preheat scheduled for {} ingredients", ingredientInfos.size());
            });
    }

    public static void onRuntimeUnavailable() {
        try {
            JeiOptRuntimeState.invalidate();
            JeiOptRuntimeState.markRuntimeUnloaded();
            Object elementSearch = JeiOptStartupContext.elementSearch();
            if (elementSearch != null) {
                AsyncSearchIndexRegistry.detach(elementSearch);
            }
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI Optimize failed to tear down preheat state", e);
        } finally {
            JeiOptStartupContext.clear();
        }
    }
}
