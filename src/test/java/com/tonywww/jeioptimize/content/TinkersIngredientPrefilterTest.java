package com.tonywww.jeioptimize.content;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TinkersIngredientPrefilterTest {
    @Test
    void alwaysIncludesDefaultTags() {
        assertEquals(
            List.of("tconstruct:modifiable", "tconstruct:parts"),
            TinkersIngredientPrefilter.configuredTagIds("")
        );
    }

    @Test
    void normalizesAndDeduplicatesAdditionalTags() {
        assertEquals(
            List.of("tconstruct:modifiable", "tconstruct:parts", "addon:tools", "addon:parts"),
            TinkersIngredientPrefilter.configuredTagIds(
                " #ADDON:TOOLS, addon:parts, addon:tools, #tconstruct:modifiable "
            )
        );
    }
}