package com.tonywww.jeioptimize.index;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class DeferredNativeSearchStorage implements InvocationHandler {
    private static final ThreadLocal<List<DeferredNativeSearchStorage>> CAPTURE = new ThreadLocal<>();
    private final Thread owner = Thread.currentThread();
    private final Method put;
    private final Method build;
    private Object builder;
    private Object storage;
    private boolean discarded;

    public DeferredNativeSearchStorage(Object builder, Class<?> builderType) throws ReflectiveOperationException {
        this.builder = builder;
        this.put = builderType.getMethod("put", String.class, Object.class);
        this.build = builderType.getMethod("build");
    }

    public Object proxy(Class<?> storageType) {
        return Proxy.newProxyInstance(storageType.getClassLoader(), new Class<?>[]{storageType}, this);
    }

    public static <T> Captured<T> capture(Supplier<T> action) {
        if (CAPTURE.get() != null) {
            throw new IllegalStateException("Nested native search builder capture");
        }
        List<DeferredNativeSearchStorage> storages = new ArrayList<>();
        CAPTURE.set(storages);
        try {
            return new Captured<>(action.get(), List.copyOf(storages));
        } catch (RuntimeException | Error failure) {
            storages.forEach(DeferredNativeSearchStorage::discard);
            throw failure;
        } finally {
            CAPTURE.remove();
        }
    }

    public static Object defer(Object builder) {
        List<DeferredNativeSearchStorage> storages = CAPTURE.get();
        if (storages == null || !builder.getClass().getName().equals("mezz.jei.common.search.BakedSubstringIndexBuilder")) {
            return null;
        }
        try {
            ClassLoader loader = builder.getClass().getClassLoader();
            Class<?> builderType = Class.forName("mezz.jei.api.search.ISearchStorageBuilder", false, loader);
            Class<?> storageType = Class.forName("mezz.jei.api.search.ISearchStorage", false, loader);
            DeferredNativeSearchStorage deferred = new DeferredNativeSearchStorage(builder, builderType);
            Object proxy = deferred.proxy(storageType);
            storages.add(deferred);
            return proxy;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Native search builder ABI changed", failure);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] arguments) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "equals" -> proxy == arguments[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "Deferred native JEI storage";
                default -> throw new IllegalStateException(method.getName());
            };
        }
        if (discarded) {
            throw new IllegalStateException("Native search build was discarded");
        }
        if (storage != null) {
            return call(method, storage, arguments);
        }
        if (method.getName().equals("put")) {
            checkOwner();
            call(put, builder, arguments);
            return null;
        }
        return switch (method.getName()) {
            case "getSearchResults", "getAllElements" -> null;
            case "statistics" -> "unpublished native builder";
            default -> throw new IllegalStateException("Unsupported pending storage method: " + method);
        };
    }

    public void finish() {
        checkOwner();
        if (discarded) { throw new IllegalStateException("Native search build was discarded"); }
        if (storage != null) { return; }
        try {
            storage = call(build, builder, null);
            if (storage == null) { throw new IllegalStateException("Native builder returned null"); }
            builder = null;
        } catch (Throwable failure) {
            discard();
            if (failure instanceof Error error) { throw error; }
            throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
        }
    }

    public void discard() {
        checkOwner();
        discarded = true;
        builder = null;
        storage = null;
    }

    private void checkOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("Native builders must stay on their client owner thread");
        }
    }

    private static Object call(Method method, Object target, Object[] arguments) throws Throwable {
        try {
            return method.invoke(target, arguments);
        } catch (InvocationTargetException failure) {
            throw failure.getCause();
        }
    }

    public record Captured<T>(T value, List<DeferredNativeSearchStorage> storages) {
        public void finish() {
            storages.forEach(DeferredNativeSearchStorage::finish);
        }

        public void discard() {
            storages.forEach(DeferredNativeSearchStorage::discard);
        }
    }
}