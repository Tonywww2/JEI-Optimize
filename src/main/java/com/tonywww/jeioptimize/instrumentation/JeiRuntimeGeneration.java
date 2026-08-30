package com.tonywww.jeioptimize.instrumentation;

public enum JeiRuntimeGeneration {
    JEI_15_LEGACY,
    JEI_15_INTERMEDIATE,
    JEI_15_MODERN,
    JEI_19_PLUS,
    UNKNOWN;

    private static final int[] JEI_15_LEGACY_MIN = {15, 20, 0, 113};
    private static final int[] JEI_15_INTERMEDIATE_MIN = {15, 24, 0, 150};
    private static final int[] JEI_15_MODERN_MIN = {15, 48, 0, 179};
    private static final int[] JEI_16_MIN = {16};
    private static final int[] JEI_19_MIN = {19};

    public static JeiRuntimeGeneration classify(String version) {
        int[] parsed = parse(version);
        if (parsed.length == 0) {
            return UNKNOWN;
        }
        if (compare(parsed, JEI_19_MIN) >= 0) {
            return JEI_19_PLUS;
        }
        if (parsed[0] != 15 || compare(parsed, JEI_16_MIN) >= 0) {
            return UNKNOWN;
        }
        if (compare(parsed, JEI_15_MODERN_MIN) >= 0) {
            return JEI_15_MODERN;
        }
        if (compare(parsed, JEI_15_INTERMEDIATE_MIN) >= 0) {
            return JEI_15_INTERMEDIATE;
        }
        if (compare(parsed, JEI_15_LEGACY_MIN) >= 0) {
            return JEI_15_LEGACY;
        }
        return UNKNOWN;
    }

    private static int[] parse(String version) {
        if (version == null || version.isBlank()) {
            return new int[0];
        }
        String[] parts = version.split("[.\\-+]");
        int[] parsed = new int[parts.length];
        for (int index = 0; index < parts.length; index++) {
            int end = 0;
            while (end < parts[index].length() && Character.isDigit(parts[index].charAt(end))) {
                end++;
            }
            if (end == 0) {
                return new int[0];
            }
            parsed[index] = Integer.parseInt(parts[index].substring(0, end));
        }
        return parsed;
    }

    private static int compare(int[] left, int[] right) {
        int length = Math.max(left.length, right.length);
        for (int index = 0; index < length; index++) {
            int leftPart = index < left.length ? left[index] : 0;
            int rightPart = index < right.length ? right[index] : 0;
            if (leftPart != rightPart) {
                return Integer.compare(leftPart, rightPart);
            }
        }
        return 0;
    }
}