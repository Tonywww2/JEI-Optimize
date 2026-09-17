package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.JeiOptCompatibilityState;
import com.tonywww.jeioptimize.mixin.accessor.ElementSearchTooltipAccessor;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collection;
import java.util.function.BooleanSupplier;

public final class TooltipSearchAdapter {
    private final Object tooltip;
    private final Object noPrefix;
    private final MethodHandle tokenPrefix;
    private final MethodHandle mode;
    private final boolean modern;

    public TooltipSearchAdapter(ElementPrefixParser parser, IElementSearch nativeSearch) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        modern = JeiOptCompatibilityState.trimTooltipStrings();
        String packageName = modern ? "mezz.jei.common.search." : "mezz.jei.core.search.";
        Class<?> prefixType = Class.forName(packageName + "PrefixInfo");
        Class<?> modeType = prefixType.getMethod("getMode").getReturnType();
        if (!modeType.isEnum() || !(modeType.getName().equals(packageName + "SearchMode")
            || modeType.getName().equals("mezz.jei.common.config.SearchMode"))) {
            throw new IllegalStateException("Unrecognized tooltip search mode");
        }
        MethodHandle getPrefix = lookup.findVirtual(prefixType, "getPrefix", MethodType.methodType(char.class));
        mode = lookup.findVirtual(prefixType, "getMode", MethodType.methodType(modeType));
        tokenPrefix = lookup.findVirtual(ElementPrefixParser.TokenInfo.class, "prefixInfo", MethodType.methodType(prefixType));
        Object foundTooltip = null;
        Object foundNoPrefix = null;
        Collection<?> prefixes = parser.allPrefixInfos();
        for (Object prefix : prefixes) {
            char character = (char) getPrefix.invoke(prefix);
            if (character == JeiOptCompatibilityState.tooltipPrefix()) {
                foundTooltip = prefix;
            }
            if (character == 0) {
                foundNoPrefix = prefix;
            }
        }
        if (foundTooltip == null || foundNoPrefix == null) {
            throw new IllegalStateException("Missing tooltip/no-prefix metadata");
        }
        tooltip = foundTooltip;
        noPrefix = foundNoPrefix;
        if (!(nativeSearch instanceof ElementSearchTooltipAccessor access)) {
            throw new IllegalStateException("Unrecognized element search implementation");
        }
        Object searchable = access.jeiopt$tooltipSearchables().get(tooltip);
        Class<?> searchableType = Class.forName(packageName + "PrefixedSearchable");
        Class<?> storageType = Class.forName(modern ? "mezz.jei.api.search.ISearchStorage" : packageName + "ISearchStorage");
        Object storage = lookup.findVirtual(searchableType, "getSearchStorage", MethodType.methodType(storageType)).invoke(searchable);
        String expected = modern ? "mezz.jei.common.search.BakedSubstringIndexSearchStorage"
            : "mezz.jei.core.search.suffixtree.GeneralizedSuffixTree";
        if (!storage.getClass().getName().equals(expected)) {
            throw new IllegalStateException("Custom tooltip search storage");
        }
        //? if forge {
        Class<?> findResult = java.util.Optional.class;
        //?} else {
        /*Class<?> findResult = mezz.jei.gui.ingredients.IListElement.class;
        *///?}
        lookup.findVirtual(IElementSearch.class, "findElement", MethodType.methodType(findResult,
            mezz.jei.api.ingredients.ITypedIngredient.class, mezz.jei.api.ingredients.IIngredientHelper.class));
    }

    public boolean modern() {
        return modern;
    }

    public static BooleanSupplier advancedSetting(Object config) {
        MethodHandle getter = AdvancedSettings.GETTER.bindTo(config);
        return () -> {
            try {
                return (boolean) getter.invokeExact();
            } catch (Throwable failure) {
                throw TooltipSearchBackend.propagate(failure);
            }
        };
    }

    private static final class AdvancedSettings {
        private static final MethodHandle GETTER = resolveBooleanSetting("mezz.jei.common.config.IIngredientFilterConfig",
            "getSearchAdvancedTooltips", "searchAdvancedTooltips");
    }

    public static boolean lowMemory(Object config) {
        try {
            return (boolean) LowMemorySettings.GETTER.invoke(config);
        } catch (Throwable failure) {
            throw TooltipSearchBackend.propagate(failure);
        }
    }

    private static final class LowMemorySettings {
        private static final MethodHandle GETTER = resolveBooleanSetting("mezz.jei.common.config.IClientConfig",
            "isLowMemorySlowSearchEnabled", "lowMemorySlowSearchEnabled");
    }

    private static MethodHandle resolveBooleanSetting(String className, String legacyName, String valueName) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> configType = Class.forName(className);
            try {
                return lookup.findVirtual(configType, legacyName, MethodType.methodType(boolean.class));
            } catch (NoSuchMethodException missingLegacyGetter) {
                Class<?> valueType = Class.forName("net.mezzdev.config.api.value.IConfigValue");
                MethodHandle value = lookup.findVirtual(configType, valueName, MethodType.methodType(valueType));
                MethodHandle get = lookup.findVirtual(valueType, "get", MethodType.methodType(Object.class));
                return MethodHandles.filterReturnValue(value, get).asType(MethodType.methodType(boolean.class, configType));
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported tooltip setting: " + valueName, failure);
        }
    }

    public String mode() {
        try {
            return ((Enum<?>) mode.invoke(tooltip)).name();
        } catch (Throwable failure) {
            throw TooltipSearchBackend.propagate(failure);
        }
    }

    public boolean includesTooltip(ElementPrefixParser.TokenInfo token) {
        try {
            String currentMode = mode();
            Object prefix = tokenPrefix.invoke(token);
            if (prefix == tooltip && !currentMode.equals("DISABLED")) {
                return true;
            }
            return currentMode.equals("ENABLED")
                && (prefix == noPrefix || ((Enum<?>) mode.invoke(prefix)).name().equals("DISABLED"));
        } catch (Throwable failure) {
            throw TooltipSearchBackend.propagate(failure);
        }
    }

}