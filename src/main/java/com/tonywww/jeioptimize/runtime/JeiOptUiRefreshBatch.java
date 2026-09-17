package com.tonywww.jeioptimize.runtime;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;

public final class JeiOptUiRefreshBatch {
    private static final ThreadLocal<JeiOptUiRefreshBatch> CURRENT = new ThreadLocal<>();
    private final IdentityHashMap<Object, Boolean> owners = new IdentityHashMap<>();
    private final List<Runnable> pending = new ArrayList<>();

    private JeiOptUiRefreshBatch() {}

    public static boolean defer(Object owner, Runnable refresh) {
        JeiOptUiRefreshBatch batch = CURRENT.get();
        if (batch == null) {
            return false;
        }
        if (batch.owners.put(owner, Boolean.TRUE) == null) {
            batch.pending.add(refresh);
        }
        return true;
    }

    public static int run(Runnable action, BooleanSupplier currentGeneration) {
        if (CURRENT.get() != null) {
            action.run();
            return 0;
        }
        JeiOptUiRefreshBatch batch = new JeiOptUiRefreshBatch();
        CURRENT.set(batch);
        try {
            action.run();
        } catch (RuntimeException | Error failure) {
            batch.pending.clear();
            throw failure;
        } finally {
            CURRENT.remove();
            batch.owners.clear();
        }
        int count = batch.pending.size();
        try {
            for (Runnable refresh : batch.pending) {
                if (!currentGeneration.getAsBoolean()) {
                    throw new CancellationException("Stale JEI layout batch");
                }
                refresh.run();
            }
            return count;
        } finally {
            batch.pending.clear();
        }
    }
}