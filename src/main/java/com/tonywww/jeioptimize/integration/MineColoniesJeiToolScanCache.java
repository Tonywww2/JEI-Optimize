package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class MineColoniesJeiToolScanCache {
    private static final Logger LOGGER = LogManager.getLogger(JeiOptimize.MOD_ID);
    private static final ThreadLocal<State> CURRENT = new ThreadLocal<>();
    private static final String CUSTOM_TOOL_TYPE_CLASS =
        "steve_gall.minecolonies_tweaks.api.common.tool.CustomToolType";
    private static final String TWEAKS_NAMESPACE = "minecolonies_tweaks";
    private static final String CUSTOM_TOOL_PREFIX = "custom_tools/";
    private static final String BLACKLIST_PREFIX = "tool_blacklists/";

    private MineColoniesJeiToolScanCache() {
    }

    public static <T> T runScoped(Supplier<T> action) {
        State existing = CURRENT.get();
        if (existing != null) {
            return action.get();
        }

        State state = new State();
        CURRENT.set(state);
        long started = System.nanoTime();
        try {
            return action.get();
        } finally {
            CURRENT.remove();
            LOGGER.info(
                "JEI Optimize MineColonies tool scan cache reused {} of {} equipment classifications "
                    + "and bypassed {} empty Tweaks rule checks in {} ms.",
                state.hits,
                state.lookups,
                state.bypassedTweaks,
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)
            );
        }
    }

    public static boolean shouldBypassEmptyTweaksRules() {
        State state = CURRENT.get();
        if (state == null || !state.canBypassTweaks()) {
            return false;
        }
        state.bypassedTweaks++;
        return true;
    }

    public static Boolean getEquipment(Object equipmentType, Object stack) {
        State state = CURRENT.get();
        return state == null ? null : state.get(state.equipment, equipmentType, stack);
    }

    public static void putEquipment(Object equipmentType, Object stack, boolean value) {
        State state = CURRENT.get();
        if (state != null) {
            state.equipment.put(equipmentType, stack, value);
        }
    }

    private static final class State {
        private final LastValue<Boolean> equipment = new LastValue<>();
        private Boolean emptyTweaksRules;
        private long lookups;
        private long hits;
        private long bypassedTweaks;

        private boolean canBypassTweaks() {
            if (emptyTweaksRules == null) {
                emptyTweaksRules = hasNoTweaksItemTags() && hasNoCustomToolTypes();
            }
            return emptyTweaksRules;
        }

        private <T> T get(LastValue<T> cache, Object first, Object second) {
            lookups++;
            T value = cache.get(first, second);
            if (value != null) {
                hits++;
            }
            return value;
        }

        private static boolean hasNoTweaksItemTags() {
            return BuiltInRegistries.ITEM.getTags().noneMatch(tag -> {
                ResourceLocation location = tag.getFirst().location();
                String path = location.getPath();
                boolean relevant = TWEAKS_NAMESPACE.equals(location.getNamespace())
                    && (path.startsWith(CUSTOM_TOOL_PREFIX) || path.startsWith(BLACKLIST_PREFIX));
                return relevant && tag.getSecond().iterator().hasNext();
            });
        }

        private static boolean hasNoCustomToolTypes() {
            try {
                Class<?> customToolType = Class.forName(
                    CUSTOM_TOOL_TYPE_CLASS,
                    false,
                    MineColoniesJeiToolScanCache.class.getClassLoader()
                );
                Method list = customToolType.getMethod("list");
                return list.invoke(null) instanceof Collection<?> values && values.isEmpty();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                return false;
            }
        }
    }

    private static final class LastValue<T> {
        private Object first;
        private Object second;
        private T value;

        private T get(Object first, Object second) {
            return this.first == first && this.second == second ? value : null;
        }

        private void put(Object first, Object second, T value) {
            this.first = first;
            this.second = second;
            this.value = value;
        }
    }
}