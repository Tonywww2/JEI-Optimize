package com.tonywww.jeioptimize.config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class JeiMainThreadPluginPolicy {
    public static final List<String> DEFAULT_PLUGIN_IDS = List.of(
        "alexscaves:alexscaves",
        "experienceobelisk:jei_plugin",
        "collectorsreap:jei_plugin",
        "theurgy:jei_plugin",
        "productivetrees:productivetrees"
    );

    private static final Pattern ENTRY_PATTERN = Pattern.compile(
        "[a-z0-9_.-]+(?::[a-z0-9/._-]+)?"
    );

    private JeiMainThreadPluginPolicy() {
    }

    static boolean isValidEntry(Object value) {
        return value instanceof String entry && ENTRY_PATTERN.matcher(entry.trim()).matches();
    }

    static Set<String> normalizeEntries(Collection<? extends String> entries) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            String trimmed = entry.trim();
            if (ENTRY_PATTERN.matcher(trimmed).matches()) {
                normalized.add(trimmed);
            }
        }
        return Set.copyOf(normalized);
    }

    static boolean matches(String pluginUid, Collection<String> configuredEntries) {
        if (configuredEntries.contains(pluginUid)) {
            return true;
        }
        int separator = pluginUid.indexOf(':');
        return separator > 0 && configuredEntries.contains(pluginUid.substring(0, separator));
    }
}