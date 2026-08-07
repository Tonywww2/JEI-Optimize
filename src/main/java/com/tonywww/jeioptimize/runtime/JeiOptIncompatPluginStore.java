package com.tonywww.jeioptimize.runtime;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.IModPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe store of JEI plugins that must not be dispatched in parallel.
 *
 * <p>Three sources feed it:
 * <ul>
 *   <li>the {@code async.parallelPluginCallExclusions} config, whose plugin uids are never sent to a
 *       worker thread and always run synchronously in JEI's normal order,</li>
 *   <li>calls that failed while running on a worker thread, which are recorded here and then drained
 *       and re-run synchronously on the main thread, in the order the plugins were registered, before
 *       JEI consumes the phase's results, and</li>
 *   <li>plugins that failed both on a worker thread and on the main-thread re-run (double failure):
 *       they are learned here as session-wide exclusions so every later phase keeps them on the main
 *       thread instead of re-running the whole fail-and-retry cycle.</li>
 * </ul>
 */
public final class JeiOptIncompatPluginStore {
    private static final Queue<Entry> ENTRIES = new ConcurrentLinkedQueue<>();

    private static volatile boolean exclusionsLoaded;
    private static volatile Set<String> excludedPlugins = Set.of();
    private static final Set<String> SESSION_EXCLUDED = ConcurrentHashMap.newKeySet();

    private JeiOptIncompatPluginStore() {
    }

    public static boolean isExcluded(String pluginUid) {
        loadExclusions();
        return excludedPlugins.contains(pluginUid) || SESSION_EXCLUDED.contains(pluginUid);
    }

    public static void learnIncompatible(String pluginUid) {
        SESSION_EXCLUDED.add(pluginUid);
    }

    public static void record(String phase, int order, IModPlugin plugin, Runnable call) {
        ENTRIES.add(new Entry(phase, order, plugin, call));
    }

    public static List<Entry> drain() {
        List<Entry> drained = new ArrayList<>(ENTRIES.size());
        Entry entry;
        while ((entry = ENTRIES.poll()) != null) {
            drained.add(entry);
        }
        drained.sort(Comparator.comparingInt(Entry::order));
        return drained;
    }

    private static void loadExclusions() {
        if (exclusionsLoaded) {
            return;
        }
        excludedPlugins = Set.copyOf(JeiOptFeatureFlags.parallelPluginCallExclusions());
        exclusionsLoaded = true;
    }

    public record Entry(String phase, int order, IModPlugin plugin, Runnable call) {
    }
}
