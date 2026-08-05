package com.tonywww.jeioptimize.runtime;

import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.IElementSearch;

import java.util.List;
import java.util.function.Function;

/**
 * Hands the ingredient list and JEI's own search factory from a constructor redirect to the
 * matching {@code RETURN} callback.
 *
 * <p>Newer JEI builds construct the whole search index inside one private factory call, so the
 * only way to keep that work off the loading screen is to intercept the call itself. The callback
 * that schedules the off-thread build cannot capture the constructor arguments without binding to
 * a specific JEI constructor descriptor, so the two halves meet here instead. JEI builds the
 * filter on the render thread, so a single slot is enough.
 */
public final class JeiOptFilterBootstrap {
    private static volatile Pending pending;

    private JeiOptFilterBootstrap() {
    }

    public static void capture(List<IListElementInfo<?>> ingredients, Function<List<IListElementInfo<?>>, IElementSearch> searchFactory) {
        pending = new Pending(List.copyOf(ingredients), searchFactory);
    }

    public static Pending take() {
        Pending taken = pending;
        pending = null;
        return taken;
    }

    public static void clear() {
        pending = null;
    }

    public record Pending(List<IListElementInfo<?>> ingredients, Function<List<IListElementInfo<?>>, IElementSearch> searchFactory) {
    }
}
