package com.tonywww.jeioptimize.recipe;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.runtime.IIngredientManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JeiRecipeGenerationLimiterTest {
    @AfterEach
    void clearContexts() {
        JeiRecipeGenerationLimiter.clearAll();
    }

    @Test
    void failedBeginDoesNotLeaveAnEmptyThreadLocalContext() {
        JeiRecipeGenerationLimiter.beginAnvil(null, 3);
        JeiRecipeGenerationLimiter.beginGrindstone(null, 3);

        assertFalse(JeiRecipeGenerationLimiter.hasAnvilContext());
        assertFalse(JeiRecipeGenerationLimiter.hasGrindstoneContext());
    }

    @Test
    void clearAllLeavesBothLimitersInactive() {
        IIngredientHelper<?> helper = proxy(IIngredientHelper.class);
        IIngredientManager manager = (IIngredientManager) Proxy.newProxyInstance(
            IIngredientManager.class.getClassLoader(),
            new Class<?>[] { IIngredientManager.class },
            (proxy, method, arguments) -> method.getName().equals("getIngredientHelper") ? helper : null
        );
        JeiRecipeGenerationLimiter.beginAnvil(manager, 3);
        JeiRecipeGenerationLimiter.beginGrindstone(manager, 3);

        assertTrue(JeiRecipeGenerationLimiter.hasAnvilContext());
        assertTrue(JeiRecipeGenerationLimiter.hasGrindstoneContext());
        JeiRecipeGenerationLimiter.clearAll();

        assertFalse(JeiRecipeGenerationLimiter.hasAnvilContext());
        assertFalse(JeiRecipeGenerationLimiter.hasGrindstoneContext());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (proxy, method, arguments) -> null
        );
    }
}