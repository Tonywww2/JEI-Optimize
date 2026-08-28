package com.tonywww.jeioptimize.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ParallelRepresentativeSelector {
    private ParallelRepresentativeSelector() {
    }

    public static <T> List<T> selectFamilies(List<T> values, Function<T, String> keyFactory, int limit) {
        if (values.size() <= limit) {
            return List.copyOf(values);
        }
        Object group = new Object();
        RepresentativeItemLimiter limiter = new RepresentativeItemLimiter(limit);
        List<T> selected = new ArrayList<>(Math.min(values.size(), limit));
        for (T value : values) {
            if (limiter.shouldKeep(group, keyFactory.apply(value))) {
                selected.add(value);
            }
        }
        return List.copyOf(selected);
    }

    public static <I, O> Selection<I, O> selectParallelFamilies(
        List<I> inputs,
        List<O> outputs,
        Function<I, String> keyFactory,
        int limit
    ) {
        requireParallel(inputs, outputs);
        if (inputs.size() <= limit) {
            return new Selection<>(List.copyOf(inputs), List.copyOf(outputs));
        }
        Object group = new Object();
        RepresentativeItemLimiter limiter = new RepresentativeItemLimiter(limit);
        List<I> selectedInputs = new ArrayList<>(Math.min(inputs.size(), limit));
        List<O> selectedOutputs = new ArrayList<>(Math.min(outputs.size(), limit));
        for (int index = 0; index < inputs.size(); index++) {
            I input = inputs.get(index);
            if (limiter.shouldKeep(group, keyFactory.apply(input))) {
                selectedInputs.add(input);
                selectedOutputs.add(outputs.get(index));
            }
        }
        return new Selection<>(List.copyOf(selectedInputs), List.copyOf(selectedOutputs));
    }

    public static <I, O> Selection<I, O> selectParallelExamples(
        List<I> inputs,
        List<O> outputs,
        int limit
    ) {
        requireParallel(inputs, outputs);
        int selectedSize = Math.min(inputs.size(), Math.max(1, limit));
        return new Selection<>(
            List.copyOf(inputs.subList(0, selectedSize)),
            List.copyOf(outputs.subList(0, selectedSize))
        );
    }

    private static void requireParallel(List<?> inputs, List<?> outputs) {
        if (inputs.size() != outputs.size()) {
            throw new IllegalArgumentException("Parallel input and output lists must have equal lengths");
        }
    }

    public record Selection<I, O>(List<I> inputs, List<O> outputs) {
    }
}