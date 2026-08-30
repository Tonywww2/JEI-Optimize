package com.tonywww.jeioptimize.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepresentativeExampleBudgetTest {
    @Test
    void sharesLimitAcrossPagesWithTheSameGroup() {
        RepresentativeExampleBudget<String> budget = new RepresentativeExampleBudget<>(16);

        assertEquals(10, budget.take("tool_repair", 10));
        assertEquals(6, budget.take("tool_repair", 10));
        assertEquals(0, budget.take("tool_repair", 1));
    }

    @Test
    void keepsIndependentLimitsForDifferentRepairRecipes() {
        RepresentativeExampleBudget<String> budget = new RepresentativeExampleBudget<>(16);

        assertEquals(16, budget.take("tool_repair", 20));
        assertEquals(16, budget.take("tool_materia_repair", 20));
    }
}