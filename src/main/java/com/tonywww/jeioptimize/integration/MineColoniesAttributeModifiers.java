package com.tonywww.jeioptimize.integration;

import java.util.Objects;
import java.util.function.Consumer;

public final class MineColoniesAttributeModifiers {
    private MineColoniesAttributeModifiers() {}

    public static boolean shouldRepair(boolean enabled, String phase, String pluginClass) {
        return enabled && "Registering recipes".equals(phase)
            && "com.minecolonies.core.compatibility.jei.JEIPlugin".equals(pluginClass);
    }

    public static <Modifier> Consumer<Modifier> equipmentConsumer(Consumer<Modifier> remove, Consumer<Modifier> add) {
        Objects.requireNonNull(remove, "remove");
        Objects.requireNonNull(add, "add");
        return modifier -> {
            remove.accept(modifier);
            add.accept(modifier);
        };
    }
}