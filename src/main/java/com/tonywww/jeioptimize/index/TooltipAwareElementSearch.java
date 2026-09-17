package com.tonywww.jeioptimize.index;

import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import mezz.jei.gui.search.IElementSearch;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class TooltipAwareElementSearch implements IElementSearch {
    private final IElementSearch delegate;
    private final TooltipSearchAdapter adapter;
    private final List<IListElement<?>> elements;
    private final TooltipSearchBackend tooltips;

    public TooltipAwareElementSearch(IElementSearch delegate, TooltipSearchAdapter adapter,
        List<IListElement<?>> elements, TooltipSearchBackend tooltips) {
        this.delegate = delegate;
        this.adapter = adapter;
        this.elements = List.copyOf(elements);
        this.tooltips = tooltips;
    }

    @Override
    public Set<IListElement<?>> getSearchResults(ElementPrefixParser.TokenInfo token) {
        Set<IListElement<?>> nativeResults = delegate.getSearchResults(token);
        if (!adapter.includesTooltip(token) || token.token().isEmpty()) {
            return nativeResults;
        }
        Set<IListElement<?>> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(nativeResults);
        BitSet ordinals = tooltips.search(token.token());
        for (int ordinal = ordinals.nextSetBit(0); ordinal >= 0; ordinal = ordinals.nextSetBit(ordinal + 1)) {
            result.add(elements.get(ordinal));
        }
        return result;
    }

    @Override
    public <T> void add(IListElementInfo<T> info, IIngredientManager ingredientManager) {
        delegate.add(info, ingredientManager);
    }

    public void addAll(Collection<IListElementInfo<?>> infos, IIngredientManager ingredientManager) {
        for (IListElementInfo<?> info : infos) {
            add(info, ingredientManager);
        }
    }

    @Override
    public Collection<IListElement<?>> getAllIngredients() {
        return delegate.getAllIngredients();
    }

    //? if forge {
    @Override
    public <T> Optional<IListElement<T>> findElement(ITypedIngredient<T> ingredient, IIngredientHelper<T> helper) {
        return delegate.findElement(ingredient, helper);
    }
    //?} else {
    /*@Override
    public <T> IListElement<T> findElement(ITypedIngredient<T> ingredient, IIngredientHelper<T> helper) {
        return delegate.findElement(ingredient, helper);
    }
    *///?}

    @Override
    public void logStatistics() {
        delegate.logStatistics();
    }
}