package com.tonywww.jeioptimize.runtime;

public final class JeiOptCompatibilityState {
    private static volatile boolean asyncStartupSupported;
    private static volatile char tooltipPrefix;
    private static volatile boolean trimTooltipStrings;

    private JeiOptCompatibilityState() {
    }

    public static boolean isAsyncStartupSupported() {
        return asyncStartupSupported;
    }

    public static void setAsyncStartupSupported(boolean supported) {
        asyncStartupSupported = supported;
    }

    public static char tooltipPrefix() {
        return tooltipPrefix;
    }

    public static boolean trimTooltipStrings() {
        return trimTooltipStrings;
    }

    public static void setTooltipContract(char prefix, boolean trimStrings) {
        trimTooltipStrings = trimStrings;
        tooltipPrefix = prefix;
    }
}