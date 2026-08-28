package com.tonywww.jeioptimize.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepresentativeItemLimiterTest {
    @Test
    void keepsThreeDistinctFamiliesPerGroup() {
        RepresentativeItemLimiter limiter = new RepresentativeItemLimiter(3);
        Object enchantment = new Object();

        assertTrue(limiter.shouldKeep(enchantment, "minecraft:diamond_sword"));
        assertFalse(limiter.shouldKeep(enchantment, "minecraft:netherite_sword"));
        assertTrue(limiter.shouldKeep(enchantment, "minecraft:diamond_pickaxe"));
        assertTrue(limiter.shouldKeep(enchantment, "minecraft:diamond_axe"));
        assertFalse(limiter.shouldKeep(enchantment, "minecraft:bow"));
        assertEquals(3, limiter.selectedCount());
        assertEquals(5, limiter.consideredCount());
    }

    @Test
    void appliesLimitIndependentlyToEachIdentityGroup() {
        RepresentativeItemLimiter limiter = new RepresentativeItemLimiter(1);
        Object firstEnchantment = new Object();
        Object secondEnchantment = new Object();

        assertTrue(limiter.shouldKeep(firstEnchantment, "minecraft:diamond_pickaxe"));
        assertFalse(limiter.shouldKeep(firstEnchantment, "minecraft:diamond_axe"));
        assertTrue(limiter.shouldKeep(secondEnchantment, "minecraft:diamond_axe"));
        assertEquals(2, limiter.groupCount());
        assertEquals(2, limiter.selectedCount());
    }

    @Test
    void keepsSixteenGenericExamples() {
        RepresentativeItemLimiter limiter = new RepresentativeItemLimiter(16);
        Object repairGroup = new Object();

        for (int index = 0; index < 20; index++) {
            assertEquals(index < 16, limiter.shouldKeep(repairGroup, "example:item" + index));
        }
        assertEquals(16, limiter.selectedCount());
        assertEquals(20, limiter.consideredCount());
    }
}