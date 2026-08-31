package com.tonywww.jeioptimize.integration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CelestialForgeReinforceInputPoolTest {
    @Test
    void aggressiveModeKeepsEveryMatchWhenTheSetIsWithinTheLimit() {
        List<String> fullMatches = List.of("diamond_sword", "netherite_sword");
        List<String> representatives = List.of("diamond_sword");

        assertEquals(
            fullMatches,
            CelestialForgeReinforceInputPool.chooseMatches(true, 3, fullMatches, representatives)
        );
    }

    @Test
    void aggressiveModeUsesRepresentativesOnlyAfterOverflowIsConfirmed() {
        List<String> firstMatches = List.of("sword", "pickaxe", "axe", "bow");
        List<String> representatives = List.of("sword", "pickaxe", "axe");

        assertEquals(
            representatives,
            CelestialForgeReinforceInputPool.chooseMatches(true, 3, firstMatches, representatives)
        );
    }

    @Test
    void losslessModeAlwaysKeepsTheFullSet() {
        List<String> fullMatches = List.of("sword", "pickaxe", "axe", "bow");

        assertEquals(
            fullMatches,
            CelestialForgeReinforceInputPool.chooseMatches(false, 3, fullMatches, List.of("sword"))
        );
    }
}