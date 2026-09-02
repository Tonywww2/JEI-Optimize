package com.tonywww.jeioptimize.runtime;

public final class JeiOptCompatibilityState {
    private static volatile boolean asyncStartupSupported;

    private JeiOptCompatibilityState() {
    }

    public static boolean isAsyncStartupSupported() {
        return asyncStartupSupported;
    }

    public static void setAsyncStartupSupported(boolean supported) {
        asyncStartupSupported = supported;
    }
}