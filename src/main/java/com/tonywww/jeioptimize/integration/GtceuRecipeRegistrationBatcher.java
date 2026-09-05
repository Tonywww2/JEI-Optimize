package com.tonywww.jeioptimize.integration;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class GtceuRecipeRegistrationBatcher {
    public static final int BATCH_SIZE = 8192;

    private GtceuRecipeRegistrationBatcher() {
    }

    public static <T> List<T> prepare(Collection<? extends T> recipes) {
        if (recipes.size() <= BATCH_SIZE) {
            return List.copyOf(recipes);
        }
        return new DeferredCopyList<>(recipes);
    }

    public static boolean isDeferredCopy(List<?> recipes) {
        return recipes instanceof DeferredCopyList<?>;
    }

    public static <T> int forEachBatch(List<T> recipes, Consumer<List<T>> registrar) {
        if (!isDeferredCopy(recipes)) {
            registrar.accept(recipes);
            return 1;
        }

        int batchCount = 0;
        List<T> batch = new ArrayList<>(Math.min(BATCH_SIZE, recipes.size()));
        for (T recipe : recipes) {
            batch.add(recipe);
            if (batch.size() == BATCH_SIZE) {
                registrar.accept(Collections.unmodifiableList(batch));
                batchCount++;
                batch = new ArrayList<>(Math.min(BATCH_SIZE, recipes.size() - batchCount * BATCH_SIZE));
            }
        }
        if (!batch.isEmpty()) {
            registrar.accept(Collections.unmodifiableList(batch));
            batchCount++;
        }
        return batchCount;
    }

    private static final class DeferredCopyList<T> extends AbstractList<T> {
        private final Collection<? extends T> source;

        private DeferredCopyList(Collection<? extends T> source) {
            for (T recipe : source) {
                Objects.requireNonNull(recipe);
            }
            this.source = source;
        }

        @Override
        public T get(int index) {
            Objects.checkIndex(index, source.size());
            Iterator<? extends T> iterator = source.iterator();
            for (int current = 0; current < index; current++) {
                iterator.next();
            }
            return iterator.next();
        }

        @Override
        public Iterator<T> iterator() {
            Iterator<? extends T> iterator = source.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public T next() {
                    return iterator.next();
                }
            };
        }

        @Override
        public int size() {
            return source.size();
        }
    }
}