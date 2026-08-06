package com.tonywww.jeioptimize.content;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Drops creative tabs the user has excluded before JEI reads their contents.
 *
 * <p>An emergency hatch for packs where one tab makes JEI's ingredient registration take minutes:
 * building that tab and resolving its subtypes is third-party code that cannot be moved off the
 * main thread, so skipping it is the only way to get back into the game. Items from a skipped tab
 * do not appear in JEI at all, which is the same state JEI already ends up in when a tab throws.
 */
public final class CreativeTabFilter {
    private CreativeTabFilter() {
    }

    public static List<CreativeModeTab> apply(List<CreativeModeTab> tabs) {
        Set<String> patterns = parse(JeiOptFeatureFlags.skipCreativeTabs());
        if (patterns.isEmpty() || tabs == null || tabs.isEmpty()) {
            return tabs;
        }

        List<CreativeModeTab> kept = new ArrayList<>(tabs.size());
        List<String> skipped = new ArrayList<>();
        Set<String> unused = new LinkedHashSet<>(patterns);
        for (CreativeModeTab tab : tabs) {
            String id = idOf(tab);
            String matched = match(patterns, id);
            if (matched == null) {
                kept.add(tab);
            } else {
                skipped.add(id);
                unused.remove(matched);
            }
        }

        if (!skipped.isEmpty()) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize skipped {} creative tab(s) on request; their items will not appear in JEI: {}",
                skipped.size(), String.join(", ", skipped));
        }
        if (!unused.isEmpty()) {
            JeiOptimize.LOGGER.warn(
                "JEI Optimize found no creative tab matching {}. Available tabs: {}",
                String.join(", ", unused),
                tabs.stream().map(CreativeTabFilter::idOf).sorted().toList());
        }
        return kept;
    }

    private static Set<String> parse(String raw) {
        Set<String> patterns = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return patterns;
        }
        for (String entry : raw.split(",")) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                patterns.add(trimmed);
            }
        }
        return patterns;
    }

    /** Matches a full {@code namespace:path} id, or a bare namespace to drop every tab of a mod. */
    private static String match(Set<String> patterns, String id) {
        if (patterns.contains(id)) {
            return id;
        }
        int separator = id.indexOf(':');
        if (separator > 0) {
            String namespace = id.substring(0, separator);
            if (patterns.contains(namespace)) {
                return namespace;
            }
        }
        return null;
    }

    private static String idOf(CreativeModeTab tab) {
        try {
            ResourceLocation key = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (key != null) {
                return key.toString();
            }
        } catch (RuntimeException | LinkageError e) {
            JeiOptimize.LOGGER.debug("JEI Optimize could not read a creative tab id", e);
        }
        return "unregistered:" + tab.getDisplayName().getString().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
