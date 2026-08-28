package com.tonywww.jeioptimize.recipe;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ParallelRepresentativeSelectorTest {
    @Test
    void familySelectionPreservesParallelOutputIndexes() {
        List<String> inputs = List.of(
            "minecraft:diamond_sword",
            "minecraft:netherite_sword",
            "minecraft:diamond_pickaxe",
            "minecraft:diamond_axe",
            "minecraft:bow"
        );
        List<Integer> outputs = List.of(1, 2, 3, 4, 5);

        ParallelRepresentativeSelector.Selection<String, Integer> selection =
            ParallelRepresentativeSelector.selectParallelFamilies(inputs, outputs, value -> value, 3);

        assertEquals(
            List.of("minecraft:diamond_sword", "minecraft:diamond_pickaxe", "minecraft:diamond_axe"),
            selection.inputs()
        );
        assertEquals(List.of(1, 3, 4), selection.outputs());
    }

    @Test
    void genericSelectionKeepsFirstSixteenParallelExamples() {
        List<Integer> inputs = IntStream.range(0, 20).boxed().toList();
        List<String> outputs = inputs.stream().map(index -> "output-" + index).toList();

        ParallelRepresentativeSelector.Selection<Integer, String> selection =
            ParallelRepresentativeSelector.selectParallelExamples(inputs, outputs, 16);

        assertEquals(IntStream.range(0, 16).boxed().toList(), selection.inputs());
        assertEquals("output-15", selection.outputs().get(15));
    }

    @Test
    void rejectsMismatchedParallelLists() {
        assertThrows(
            IllegalArgumentException.class,
            () -> ParallelRepresentativeSelector.selectParallelFamilies(
                List.of("minecraft:diamond_sword"),
                List.of(),
                value -> value,
                3
            )
        );
    }
}