package com.tonywww.jeioptimize.snapshot;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.runtime.IIngredientFilter;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public final class IngredientSearchSnapshotBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IngredientSearchSnapshotBuilder.class);

    private final IIngredientManager ingredientManager;
    private final IIngredientFilterConfig ingredientFilterConfig;
    private final IColorHelper colorHelper;

    public IngredientSearchSnapshotBuilder(IIngredientManager ingredientManager, IIngredientFilterConfig ingredientFilterConfig, IColorHelper colorHelper) {
        this.ingredientManager = Objects.requireNonNull(ingredientManager, "ingredientManager");
        this.ingredientFilterConfig = Objects.requireNonNull(ingredientFilterConfig, "ingredientFilterConfig");
        this.colorHelper = Objects.requireNonNull(colorHelper, "colorHelper");
    }

    public static List<IngredientSearchSnapshot> fromElementInfos(
        Collection<? extends IListElementInfo<?>> elementInfos,
        IIngredientManager ingredientManager,
        IIngredientFilterConfig ingredientFilterConfig,
        IColorHelper colorHelper
    ) {
        return new IngredientSearchSnapshotBuilder(ingredientManager, ingredientFilterConfig, colorHelper)
            .buildFromElementInfos(elementInfos);
    }

    public static List<IngredientSearchSnapshot> fromElements(
        Collection<? extends IListElement<?>> elements,
        IIngredientManager ingredientManager,
        IIngredientFilterConfig ingredientFilterConfig,
        IColorHelper colorHelper,
        ElementInfoFactory elementInfoFactory
    ) {
        return new IngredientSearchSnapshotBuilder(ingredientManager, ingredientFilterConfig, colorHelper)
            .buildFromElements(elements, elementInfoFactory);
    }

    public List<IngredientSearchSnapshot> buildFromElementInfos(Collection<? extends IListElementInfo<?>> elementInfos) {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return List.of();
        }
        if (elementInfos == null || elementInfos.isEmpty()) {
            return List.of();
        }

        List<IngredientSearchSnapshot> snapshots = new ArrayList<>(elementInfos.size());
        for (IListElementInfo<?> elementInfo : elementInfos) {
            createSnapshot(elementInfo).ifPresent(snapshots::add);
        }
        return List.copyOf(snapshots);
    }

    public List<IngredientSearchSnapshot> buildFromElements(Collection<? extends IListElement<?>> elements, ElementInfoFactory elementInfoFactory) {
        if (!JeiOptFeatureFlags.searchPreheat()) {
            return List.of();
        }
        if (elements == null || elements.isEmpty()) {
            return List.of();
        }
        Objects.requireNonNull(elementInfoFactory, "elementInfoFactory");

        List<IngredientSearchSnapshot> snapshots = new ArrayList<>(elements.size());
        for (IListElement<?> element : elements) {
            Optional<? extends IListElementInfo<?>> elementInfo = elementInfoFactory.create(element, ingredientManager);
            elementInfo.flatMap(this::createSnapshot).ifPresent(snapshots::add);
        }
        return List.copyOf(snapshots);
    }

    private Optional<IngredientSearchSnapshot> createSnapshot(IListElementInfo<?> elementInfo) {
        try {
            ITypedIngredient<?> typedIngredient = elementInfo.getTypedIngredient();
            Object ingredient = typedIngredient.getIngredient();
            IIngredientHelper<Object> ingredientHelper = getIngredientHelper(typedIngredient);
            Object uid = ingredientHelper.getUniqueId(ingredient, UidContext.Ingredient);

            IListElement<?> element = elementInfo.getElement();
            boolean visible = element.isVisible();

            return Optional.of(new IngredientSearchSnapshot(
                uid,
                immutableStringList(elementInfo.getNames()),
                immutableStringList(elementInfo.getModNames()),
                immutableStringList(elementInfo.getModIds()),
                immutableStringList(elementInfo.getTooltipStrings(ingredientFilterConfig, ingredientManager)),
                immutableStringList(elementInfo.getTagStrings(ingredientManager)),
                immutableStringList(elementInfo.getCreativeTabsStrings(ingredientManager)),
                colorStrings(elementInfo),
                resourceLocationString(elementInfo.getResourceLocation()),
                visible,
                elementInfo.getCreatedIndex()
            ));
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to create JEI ingredient search snapshot", e);
            return Optional.empty();
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private IIngredientHelper<Object> getIngredientHelper(ITypedIngredient<?> typedIngredient) {
        return (IIngredientHelper) ingredientManager.getIngredientHelper(typedIngredient.getType());
    }

    private List<String> colorStrings(IListElementInfo<?> elementInfo) {
        List<String> colorNames = new ArrayList<>();
        for (Object color : elementInfo.getColors(ingredientManager)) {
            if (color instanceof Integer colorValue) {
                colorNames.add(colorHelper.getClosestColorName(colorValue).toLowerCase(java.util.Locale.ROOT));
            }
        }
        return List.copyOf(colorNames);
    }

    private static String resourceLocationString(Object resourceLocation) {
        if (resourceLocation instanceof ResourceLocation location) {
            return location.toString();
        }
        return String.valueOf(resourceLocation);
    }

    private static List<String> immutableStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            List<String> strings = new ArrayList<>(collection.size());
            for (Object element : collection) {
                if (element != null) {
                    strings.add(String.valueOf(element));
                }
            }
            return List.copyOf(strings);
        }
        if (value instanceof Stream<?> stream) {
            return stream
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        }
        return Collections.singletonList(String.valueOf(value));
    }

    @FunctionalInterface
    public interface ElementInfoFactory {
        Optional<? extends IListElementInfo<?>> create(IListElement<?> element, IIngredientManager ingredientManager);
    }
}