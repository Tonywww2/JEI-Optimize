package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.gui.ingredients.IListElementInfo;

import java.util.List;

/**
 * Cheap capture holder for JEI internal instances observed during construction.
 *
 * <p>Mixins store references here without doing any heavy work. The JEI plugin then consumes
 * them after JEI startup completes (in {@code onRuntimeAvailable}), off the startup critical path.
 */
public final class JeiOptStartupContext {
    private static volatile Object elementSearch;
    private static volatile List<? extends IListElementInfo<?>> ingredientInfos;
    private static volatile IIngredientManager ingredientManager;
    private static volatile IIngredientFilterConfig ingredientFilterConfig;
    private static volatile IColorHelper colorHelper;

    private JeiOptStartupContext() {
    }

    public static void captureIngredientFilter(
        Object elementSearch,
        List<? extends IListElementInfo<?>> ingredientInfos,
        IIngredientManager ingredientManager,
        Object ingredientFilterConfig,
        IColorHelper colorHelper
    ) {
        try {
            JeiOptStartupContext.elementSearch = elementSearch;
            JeiOptStartupContext.ingredientInfos = ingredientInfos == null ? List.of() : List.copyOf(ingredientInfos);
            JeiOptStartupContext.ingredientManager = ingredientManager;
            JeiOptStartupContext.colorHelper = colorHelper;
            if (ingredientFilterConfig instanceof IIngredientFilterConfig filterConfig) {
                JeiOptStartupContext.ingredientFilterConfig = filterConfig;
            } else {
                JeiOptStartupContext.ingredientFilterConfig = null;
            }
            JeiOptimize.LOGGER.debug(
                "JEI Optimize captured ingredient filter: elementSearch={}, infos={}, ingredientManager={}, configType={}, colorHelper={}",
                elementSearch != null,
                JeiOptStartupContext.ingredientInfos.size(),
                ingredientManager != null,
                ingredientFilterConfig == null ? "null" : ingredientFilterConfig.getClass().getName(),
                colorHelper != null
            );
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.warn("JEI Optimize failed to capture ingredient filter context", e);
            clear();
        }
    }

    public static Object elementSearch() {
        return elementSearch;
    }

    public static List<? extends IListElementInfo<?>> ingredientInfos() {
        return ingredientInfos;
    }

    public static IIngredientManager ingredientManager() {
        return ingredientManager;
    }

    public static IIngredientFilterConfig ingredientFilterConfig() {
        return ingredientFilterConfig;
    }

    public static IColorHelper colorHelper() {
        return colorHelper;
    }

    public static void clear() {
        elementSearch = null;
        ingredientInfos = null;
        ingredientManager = null;
        ingredientFilterConfig = null;
        colorHelper = null;
    }
}
