package com.tonywww.jeioptimize.recipe;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public final class MenuUpdateSuppressor {
    private static final ThreadLocal<Deque<Object>> SUPPRESSED = ThreadLocal.withInitial(ArrayDeque::new);

    private MenuUpdateSuppressor() {
    }

    public static Scope suppress(Object menu) {
        if (menu == null) {
            return Scope.EMPTY;
        }
        SUPPRESSED.get().push(menu);
        return new Scope(menu);
    }

    public static boolean isSuppressed(Object menu) {
        if (menu == null) {
            return false;
        }
        for (Object suppressed : SUPPRESSED.get()) {
            if (suppressed == menu) {
                return true;
            }
        }
        return false;
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope EMPTY = new Scope(null);

        private final Object menu;
        private boolean closed;

        private Scope(Object menu) {
            this.menu = menu;
        }

        @Override
        public void close() {
            if (closed || menu == null) {
                return;
            }
            closed = true;
            Deque<Object> stack = SUPPRESSED.get();
            Iterator<Object> iterator = stack.iterator();
            while (iterator.hasNext()) {
                if (iterator.next() == menu) {
                    iterator.remove();
                    break;
                }
            }
            if (stack.isEmpty()) {
                SUPPRESSED.remove();
            }
        }
    }
}