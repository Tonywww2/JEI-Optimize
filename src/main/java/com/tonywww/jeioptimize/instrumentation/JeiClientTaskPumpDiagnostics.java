package com.tonywww.jeioptimize.instrumentation;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;

public final class JeiClientTaskPumpDiagnostics {
    private static final Object LOCK = new Object();

    private static long reportedGeneration = Long.MIN_VALUE;
    private static long hitCount;

    private JeiClientTaskPumpDiagnostics() {
    }

    public static void onBlockedClientTaskPump() {
        if (!JeiOptExecutors.isJeiStartThread()) {
            return;
        }

        long generation = JeiOptRuntimeState.currentGeneration();
        long currentHitCount;
        synchronized (LOCK) {
            if (reportedGeneration != generation) {
                reportedGeneration = generation;
                hitCount = 0L;
            }
            currentHitCount = ++hitCount;
        }

        JeiPluginCallContext.ActivePluginCall call = JeiPluginCallContext.currentCall().orElse(null);
        if (currentHitCount == 1L) {
            String phase = call != null ? call.phase() : "<JEI core>";
            String pluginUid = call != null ? call.pluginUid().toString() : "<none>";
            String pluginClass = call != null ? call.pluginClass() : "<none>";
            IllegalStateException callSite = new IllegalStateException(
                "First blocked off-main Minecraft Client task-pump call during JEI startup"
            );
            JeiOptimize.LOGGER.error(
                "JEI Optimize blocked the JEI startup thread from pumping Minecraft Client tasks. "
                    + "generation={}, phase='{}', plugin={}, class={}. The Render thread remains the sole "
                    + "consumer; add this plugin to mainThreadPlugins and report this complete stack.",
                generation,
                phase,
                pluginUid,
                pluginClass,
                callSite
            );
        } else if (isPowerOfTen(currentHitCount)) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize has blocked {} off-main Minecraft Client task-pump calls in startup generation {}.",
                currentHitCount,
                generation
            );
        }
    }

    private static boolean isPowerOfTen(long value) {
        while (value > 1L && value % 10L == 0L) {
            value /= 10L;
        }
        return value == 1L;
    }
}