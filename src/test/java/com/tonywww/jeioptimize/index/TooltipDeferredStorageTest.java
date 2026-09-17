package com.tonywww.jeioptimize.index;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.zip.ZipFile;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class TooltipDeferredStorageTest {
    public interface Builder {
        void put(String text, Object value);
        Storage build();
    }

    public interface Storage {
        void put(String text, Object value);
        void getSearchResults(String query, Consumer<Collection<Object>> output);
        void getAllElements(Consumer<Collection<Object>> output);
        String statistics();
    }

    public static void main(String[] arguments) throws Exception {
        List<Object> values = new ArrayList<>();
        int[] builds = {0};
        Storage nativeStorage = new Storage() {
            public void put(String text, Object value) { values.add(value); }
            public void getSearchResults(String query, Consumer<Collection<Object>> output) { output.accept(List.copyOf(values)); }
            public void getAllElements(Consumer<Collection<Object>> output) { output.accept(List.copyOf(values)); }
            public String statistics() { return "native"; }
        };
        Builder builder = new Builder() {
            public void put(String text, Object value) { values.add(value); }
            public Storage build() { builds[0]++; return nativeStorage; }
        };
        DeferredNativeSearchStorage deferred = new DeferredNativeSearchStorage(builder, Builder.class);
        Storage proxy = (Storage) deferred.proxy(Storage.class);
        proxy.put("initial", 0);
        proxy.getSearchResults("initial", result -> { throw new AssertionError("partial query published"); });
        check(builds[0] == 0 && values.equals(List.of(0)), "initial put uses builder");
        check(CompletableFuture.supplyAsync(() -> {
            try { proxy.put("wrong thread", 1); return false; } catch (IllegalStateException expected) { return true; }
        }).join(), "off-thread builder mutation rejected");
        deferred.finish();
        deferred.finish();
        check(builds[0] == 1, "build exactly once");
        proxy.put("runtime", 1);
        proxy.getSearchResults("", result -> check(result.equals(List.of(0, 1)), "runtime writes delegate"));
        check(proxy.statistics().equals("native"), "statistics delegate");
        DeferredNativeSearchStorage abandoned = new DeferredNativeSearchStorage(builder, Builder.class);
        abandoned.discard();
        try { abandoned.finish(); throw new AssertionError("discard published"); } catch (IllegalStateException expected) {}
        try {
            DeferredNativeSearchStorage.capture(() -> { throw new IllegalArgumentException("factory failed"); });
        } catch (IllegalArgumentException expected) {}
        check(DeferredNativeSearchStorage.capture(() -> "restored").value().equals("restored"), "capture restored after failure");
        Builder failingBuilder = new Builder() {
            public void put(String text, Object value) {}
            public Storage build() { throw new IllegalArgumentException("seal failure"); }
        };
        DeferredNativeSearchStorage failing = new DeferredNativeSearchStorage(failingBuilder, Builder.class);
        try { failing.finish(); throw new AssertionError("seal failure hidden"); } catch (IllegalArgumentException expected) {}
        try { failing.finish(); throw new AssertionError("failed builder reused"); } catch (IllegalStateException expected) {}
        for (String argument : arguments) {
            try (ZipFile archive = new ZipFile(argument)) {
                if (archive.getEntry("mezz/jei/common/search/BakedSubstringIndexBuilder.class") == null) {
                    continue;
                }
            }
            verifyNativeBuilder(Path.of(argument));
        }
        System.out.println("TooltipDeferredStorageTest passed: builder retention, no partial queries, owner, finalization, runtime addition");
    }

    private static void verifyNativeBuilder(Path archive) throws Exception {
        java.net.URL fastutil = Class.forName("it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap")
            .getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{archive.toUri().toURL(), fastutil}, ClassLoader.getPlatformClassLoader())) {
            Class<?> builderClass = loader.loadClass("mezz.jei.common.search.BakedSubstringIndexBuilder");
            Class<?> builderType = loader.loadClass("mezz.jei.api.search.ISearchStorageBuilder");
            Class<?> storageType = loader.loadClass("mezz.jei.api.search.ISearchStorage");
            Object baselineBuilder = builderClass.getConstructor().newInstance();
            Object deferredBuilder = builderClass.getConstructor().newInstance();
            var captured = DeferredNativeSearchStorage.capture(() -> DeferredNativeSearchStorage.defer(deferredBuilder));
            check(captured.storages().size() == 1, "stock builder captured");
            Method builderPut = builderType.getMethod("put", String.class, Object.class);
            Method storagePut = storageType.getMethod("put", String.class, Object.class);
            String[] texts = {"iron", "attack durability", "energy mana", "\u4e2d\u6587", "\ud83d\ude00", "a", "  spaces  ", "duplicate"};
            for (int ordinal = 0; ordinal < 1200; ordinal++) {
                Integer identity = ordinal;
                String text = texts[ordinal % texts.length];
                builderPut.invoke(baselineBuilder, text, identity);
                storagePut.invoke(captured.value(), text, identity);
                builderPut.invoke(baselineBuilder, "duplicate", identity);
                storagePut.invoke(captured.value(), "duplicate", identity);
            }
            Object baseline = builderType.getMethod("build").invoke(baselineBuilder);
            captured.finish();
            Method query = storageType.getMethod("getSearchResults", String.class, Consumer.class);
            for (String text : List.of("", "i", "iron", "attack", "durability", "mana", "\u4e2d", "\ud83d", " ", "absent", "duplicate")) {
                check(results(query, baseline, text).equals(results(query, captured.value(), text)), "native query differs: " + text);
            }
            storagePut.invoke(baseline, "runtime-unique", 2000);
            storagePut.invoke(captured.value(), "runtime-unique", 2000);
            check(results(query, baseline, "runtime").equals(results(query, captured.value(), "runtime")), "native runtime addition differs");
            System.out.println("Deferred native builder differential passed: " + archive.getFileName());
        }
    }

    private static Set<Object> results(Method query, Object storage, String text) throws Exception {
        Set<Object> result = new HashSet<>();
        Consumer<Collection<Object>> collect = result::addAll;
        query.invoke(storage, text, collect);
        return result;
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}