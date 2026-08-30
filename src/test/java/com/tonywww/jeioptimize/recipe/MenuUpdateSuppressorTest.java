package com.tonywww.jeioptimize.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuUpdateSuppressorTest {
    @Test
    void nestedScopesRestoreTheOuterMenu() {
        Object outer = new Object();
        Object inner = new Object();

        try (MenuUpdateSuppressor.Scope ignoredOuter = MenuUpdateSuppressor.suppress(outer)) {
            assertTrue(MenuUpdateSuppressor.isSuppressed(outer));
            try (MenuUpdateSuppressor.Scope ignoredInner = MenuUpdateSuppressor.suppress(inner)) {
                assertTrue(MenuUpdateSuppressor.isSuppressed(outer));
                assertTrue(MenuUpdateSuppressor.isSuppressed(inner));
            }
            assertTrue(MenuUpdateSuppressor.isSuppressed(outer));
            assertFalse(MenuUpdateSuppressor.isSuppressed(inner));
        }
        assertFalse(MenuUpdateSuppressor.isSuppressed(outer));
    }
}