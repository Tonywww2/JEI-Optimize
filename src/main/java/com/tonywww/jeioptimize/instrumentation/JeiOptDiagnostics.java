package com.tonywww.jeioptimize.instrumentation;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class JeiOptDiagnostics {
    private static final long LOG_THRESHOLD_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    private JeiOptDiagnostics() {
    }

    public static void callPluginWithTiming(String title, IModPlugin plugin, Runnable call) {
        if (!JeiOptFeatureFlags.pluginTiming()) {
            call.run();
            return;
        }

        ResourceLocation pluginUid = safePluginUid(plugin);
        long startTime = System.nanoTime();
        try {
            call.run();
        } finally {
            long elapsedNanos = System.nanoTime() - startTime;
            if (elapsedNanos >= LOG_THRESHOLD_NANOS) {
                JeiOptimize.LOGGER.info("JEI plugin phase '{}' for {} took {}", title, pluginUid, formatDuration(elapsedNanos));
            }
        }
    }

    public static void reportRegistrationCounts() {
        if (!JeiOptFeatureFlags.registrationCounts()) {
            return;
        }
        Map<ResourceLocation, Map<JeiPluginCallContext.RegistrationMetric, Long>> counts =
            JeiPluginCallContext.snapshotCounts();
        if (counts.isEmpty()) {
            JeiOptimize.LOGGER.info("JEI Optimize registration counts: none recorded");
            return;
        }
        counts.entrySet().stream()
            .sorted(Comparator.comparingLong(
                (Map.Entry<ResourceLocation, Map<JeiPluginCallContext.RegistrationMetric, Long>> entry) ->
                    entry.getValue().getOrDefault(JeiPluginCallContext.RegistrationMetric.RECIPES, 0L)).reversed())
            .forEach(entry -> JeiOptimize.LOGGER.info(
                "JEI Optimize registration counts for {}: {}", entry.getKey(), formatCounts(entry.getValue())));
        JeiPluginCallContext.clearCounts();
    }

    private static String formatCounts(Map<JeiPluginCallContext.RegistrationMetric, Long> counts) {
        StringBuilder builder = new StringBuilder();
        for (JeiPluginCallContext.RegistrationMetric metric : JeiPluginCallContext.RegistrationMetric.values()) {
            Long value = counts.get(metric);
            if (value != null && value > 0) {
                if (builder.length() > 0) {
                    builder.append(", ");
                }
                builder.append(metric).append('=').append(value);
            }
        }
        return builder.toString();
    }

    private static ResourceLocation safePluginUid(IModPlugin plugin) {
        try {
            return plugin.getPluginUid();
        } catch (RuntimeException | LinkageError e) {
            return new ResourceLocation(JeiOptimize.MOD_ID, "unknown_plugin");
        }
    }

    private static String formatDuration(long elapsedNanos) {
        if (elapsedNanos >= TimeUnit.SECONDS.toNanos(1)) {
            return String.format(Locale.ROOT, "%.3f s", elapsedNanos / 1_000_000_000.0);
        }
        if (elapsedNanos >= TimeUnit.MILLISECONDS.toNanos(1)) {
            return String.format(Locale.ROOT, "%.3f ms", elapsedNanos / 1_000_000.0);
        }
        if (elapsedNanos >= TimeUnit.MICROSECONDS.toNanos(1)) {
            return String.format(Locale.ROOT, "%.3f us", elapsedNanos / 1_000.0);
        }
        return elapsedNanos + " ns";
    }
}