package com.tonywww.jeioptimize.index;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.BitSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Consumer;

public final class TooltipSearchBackend {
    private final Object storage;
    private final MethodHandle put;
    private final MethodHandle query;
    private final ArrayList<Integer> ordinals = new ArrayList<>();

    public TooltipSearchBackend(boolean modern) throws ReflectiveOperationException {
        String name = modern ? "mezz.jei.common.search.GeneralizedSuffixTreeSearchStorage"
            : "mezz.jei.core.search.suffixtree.GeneralizedSuffixTree";
        Class<?> type = Class.forName(name);
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        put = lookup.findVirtual(type, "put", MethodType.methodType(void.class, String.class, Object.class));
        query = lookup.findVirtual(type, "getSearchResults", MethodType.methodType(void.class, String.class, Consumer.class));
        try {
            storage = lookup.findConstructor(type, MethodType.methodType(void.class)).invoke();
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }

    public void put(String text, int ordinal) {
        while (ordinals.size() <= ordinal) {
            ordinals.add(Integer.valueOf(ordinals.size()));
        }
        try {
            put.invoke(storage, text, ordinals.get(ordinal));
        } catch (Throwable failure) {
            throw propagate(failure);
        }
    }

    public BitSet search(String token) {
        BitSet result = new BitSet();
        if (token.isEmpty()) {
            return result;
        }
        Consumer<Collection<Integer>> receiver = values -> values.forEach(result::set);
        try {
            query.invoke(storage, token, receiver);
        } catch (Throwable failure) {
            throw propagate(failure);
        }
        return result;
    }

    static RuntimeException propagate(Throwable failure) {
        if (failure instanceof Error error) {
            throw error;
        }
        return failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
    }
}