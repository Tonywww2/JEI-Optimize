package com.tonywww.jeioptimize.instrumentation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiRuntimeGenerationTest {
    @Test
    void classifiesVerifiedJeiBoundaries() {
        assertEquals(JeiRuntimeGeneration.UNKNOWN, JeiRuntimeGeneration.classify("15.20.0.112"));
        assertEquals(JeiRuntimeGeneration.JEI_15_LEGACY, JeiRuntimeGeneration.classify("15.20.0.113"));
        assertEquals(JeiRuntimeGeneration.JEI_15_LEGACY, JeiRuntimeGeneration.classify("15.20.0.120"));
        assertEquals(JeiRuntimeGeneration.JEI_15_INTERMEDIATE, JeiRuntimeGeneration.classify("15.24.0.150"));
        assertEquals(JeiRuntimeGeneration.JEI_15_INTERMEDIATE, JeiRuntimeGeneration.classify("15.48.0.178"));
        assertEquals(JeiRuntimeGeneration.JEI_15_MODERN, JeiRuntimeGeneration.classify("15.48.0.179"));
        assertEquals(JeiRuntimeGeneration.JEI_19_PLUS, JeiRuntimeGeneration.classify("19.27.0.340"));
    }

    @Test
    void rejectsMissingAndUnrecognizedVersions() {
        assertEquals(JeiRuntimeGeneration.UNKNOWN, JeiRuntimeGeneration.classify(null));
        assertEquals(JeiRuntimeGeneration.UNKNOWN, JeiRuntimeGeneration.classify("unknown"));
        assertEquals(JeiRuntimeGeneration.UNKNOWN, JeiRuntimeGeneration.classify("16.0.0"));
    }
}