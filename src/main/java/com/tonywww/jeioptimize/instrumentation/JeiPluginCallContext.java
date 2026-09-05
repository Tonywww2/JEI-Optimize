package com.tonywww.jeioptimize.instrumentation;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.IModPlugin;
import net.minecraft.resources.ResourceLocation;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class JeiPluginCallContext {
    private static final ThreadLocal<ResourceLocation> CURRENT_PLUGIN = new ThreadLocal<>();
    private static final ThreadLocal<ActivePluginCall> CURRENT_CALL = new ThreadLocal<>();
    private static final Object LOCK = new Object();
    private static final Map<ResourceLocation, EnumMap<RegistrationMetric, Long>> COUNTS = new LinkedHashMap<>();

    private JeiPluginCallContext() {
    }

    public static void pushPlugin(ResourceLocation pluginUid) {
        CURRENT_PLUGIN.set(pluginUid);
    }

    public static void popPlugin() {
        CURRENT_PLUGIN.remove();
    }

    public static void runWithPlugin(String phase, IModPlugin plugin, Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        ResourceLocation previousPlugin = CURRENT_PLUGIN.get();
        ActivePluginCall previousCall = CURRENT_CALL.get();
        ResourceLocation pluginUid = safePluginUid(plugin);
        pushPlugin(pluginUid);
        CURRENT_CALL.set(new ActivePluginCall(phase, pluginUid, plugin.getClass().getName()));
        try {
            runnable.run();
        } finally {
            if (previousPlugin == null) {
                popPlugin();
            } else {
                CURRENT_PLUGIN.set(previousPlugin);
            }
            if (previousCall == null) {
                CURRENT_CALL.remove();
            } else {
                CURRENT_CALL.set(previousCall);
            }
        }
    }

    public static Optional<ResourceLocation> currentPlugin() {
        return Optional.ofNullable(CURRENT_PLUGIN.get());
    }

    public static Optional<ActivePluginCall> currentCall() {
        return Optional.ofNullable(CURRENT_CALL.get());
    }

    public static void record(RegistrationMetric metric, long amount) {
        if (!JeiOptFeatureFlags.registrationCounts() || amount <= 0) {
            return;
        }
        ResourceLocation pluginUid = CURRENT_PLUGIN.get();
        if (pluginUid == null) {
            return;
        }
        synchronized (LOCK) {
            COUNTS.computeIfAbsent(pluginUid, ignored -> new EnumMap<>(RegistrationMetric.class))
                .merge(metric, amount, Long::sum);
        }
    }

    public static Map<ResourceLocation, Map<RegistrationMetric, Long>> snapshotCounts() {
        synchronized (LOCK) {
            Map<ResourceLocation, Map<RegistrationMetric, Long>> snapshot = new LinkedHashMap<>();
            COUNTS.forEach((pluginUid, counts) -> snapshot.put(pluginUid, Map.copyOf(counts)));
            return Map.copyOf(snapshot);
        }
    }

    public static void clearCounts() {
        synchronized (LOCK) {
            COUNTS.clear();
        }
    }

    public enum RegistrationMetric {
        INGREDIENT_TYPES,
        EXTRA_INGREDIENTS,
        INGREDIENT_ALIASES,
        RECIPES,
        INGREDIENT_INFO_RECIPES,
        RECIPE_CATEGORIES,
        RECIPE_CATALYSTS
    }

    public record ActivePluginCall(String phase, ResourceLocation pluginUid, String pluginClass) {
    }

    private static ResourceLocation safePluginUid(IModPlugin plugin) {
        try {
            return plugin.getPluginUid();
        } catch (RuntimeException | LinkageError e) {
            //? if forge {
            return new ResourceLocation(JeiOptimize.MOD_ID, "unknown_plugin");
            //?} else {
            /*return ResourceLocation.fromNamespaceAndPath(JeiOptimize.MOD_ID, "unknown_plugin");
            *///?}
        }
    }
}