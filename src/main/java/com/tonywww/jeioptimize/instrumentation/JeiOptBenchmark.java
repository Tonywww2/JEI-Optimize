package com.tonywww.jeioptimize.instrumentation;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import jdk.jfr.Event;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public final class JeiOptBenchmark {
    public static final boolean ENABLED = Boolean.getBoolean("jet.benchmark");
    private static final String[] QUERIES = {"", "iron", "$attack", "$durability", "$energy", "$mana",
        "@minecraft", "@minecolonies", "$tool", "$a", "$zzzzzznotfound", "diamond"};
    private static IJeiRuntime runtime;
    private static Object level;
    private static long worldStarted;
    private static long sidebarStarted;
    private static long lastFrame;
    private static int queryCursor;
    private static int readyTicks;
    private static boolean done;
    private static boolean opened;
    private static String previousFilter;

    private JeiOptBenchmark() {}

    public static void runtimeAvailable(IJeiRuntime available) {
        if (ENABLED) {
            runtime = available;
        }
    }

    public static void clear() {
        runtime = null;
        level = null;
        previousFilter = null;
        worldStarted = 0;
        sidebarStarted = 0;
        lastFrame = 0;
        queryCursor = 0;
        readyTicks = 0;
        done = false;
        opened = false;
    }

    public static void tick(long tickStarted) {
        if (!ENABLED) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (level != minecraft.level) {
            level = minecraft.level;
            worldStarted = System.nanoTime();
            mark("world-ready", 0);
        }
        if (done) {
            return;
        }
        Sample tick = new Sample();
        tick.kind = "tick";
        tick.nanos = System.nanoTime() - tickStarted;
        tick.generation = JeiOptRuntimeState.currentGeneration();
        tick.commit();
        if (!opened && minecraft.screen == null) {
            minecraft.setScreen(new InventoryScreen(minecraft.player));
            opened = true;
            JeiOptimize.LOGGER.info("JET benchmark inventory opened: generation={}, startupBlocked={}",
                JeiOptRuntimeState.currentGeneration(), JeiOptStartupProgressState.blocksJeiInput());
        }
        if (runtime == null || JeiOptStartupProgressState.blocksJeiInput() || sidebarStarted == 0) {
            return;
        }
        if (++readyTicks <= 60) {
            return;
        }
        if (readyTicks == 61 && Boolean.getBoolean("jet.benchmark.requireBookmarks")) {
            Object bookmarks = runtime.getBookmarkOverlay();
            try {
                if (!Boolean.TRUE.equals(bookmarks.getClass().getMethod("isListDisplayed").invoke(bookmarks))) {
                    throw new IllegalStateException("Saved bookmark overlay is not displayed");
                }
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Could not verify saved bookmark overlay", failure);
            }
            JeiOptimize.LOGGER.info("JET benchmark saved bookmark overlay verified after publication");
        }
        var filter = runtime.getIngredientFilter();
        if (previousFilter == null) {
            previousFilter = filter.getFilterText();
            mark("query-start", System.nanoTime() - worldStarted);
        }
        if (queryCursor < QUERIES.length * 10) {
            int index = queryCursor % QUERIES.length;
            Sample query = new Sample();
            query.kind = "query";
            query.generation = JeiOptRuntimeState.currentGeneration();
            query.index = index;
            long started = System.nanoTime();
            filter.setFilterText(QUERIES[index]);
            query.count = filter.getFilteredIngredients(VanillaTypes.ITEM_STACK).size();
            query.nanos = System.nanoTime() - started;
            query.commit();
            queryCursor++;
            return;
        }
        filter.setFilterText(previousFilter);
        done = true;
        mark("complete", System.nanoTime() - worldStarted);
        JeiOptimize.LOGGER.info("JET benchmark complete: queries={}, generation={}", queryCursor, JeiOptRuntimeState.currentGeneration());
        if (Boolean.getBoolean("jet.benchmark.autoExit")) {
            minecraft.stop();
        }
    }

    public static void frame() {
        if (!ENABLED || worldStarted == 0 || done) {
            return;
        }
        long now = System.nanoTime();
        if (lastFrame != 0) {
            Sample frame = new Sample();
            frame.kind = "frame";
            frame.nanos = now - lastFrame;
            frame.generation = JeiOptRuntimeState.currentGeneration();
            frame.commit();
        }
        lastFrame = now;
    }

    public static void sidebarDrawn() {
        if (!ENABLED || sidebarStarted != 0 || worldStarted == 0 || runtime == null
            || JeiOptStartupProgressState.blocksJeiInput() || !runtime.getIngredientListOverlay().isListDisplayed()
            || runtime.getIngredientListOverlay().getVisibleIngredients(VanillaTypes.ITEM_STACK).isEmpty()) {
            return;
        }
        sidebarStarted = System.nanoTime();
        mark("sidebar-drawn", sidebarStarted - worldStarted);
        JeiOptimize.LOGGER.info("JET benchmark sidebar drawn: worldToSidebarMs={}", (sidebarStarted - worldStarted) / 1_000_000L);
    }

    private static void mark(String kind, long nanos) {
        Sample sample = new Sample();
        sample.kind = kind;
        sample.nanos = nanos;
        sample.generation = JeiOptRuntimeState.currentGeneration();
        sample.commit();
    }

    @Name("jet.Benchmark")
    @StackTrace(false)
    public static final class Sample extends Event {
        public String kind;
        public long nanos;
        public long generation;
        public int index;
        public int count;
    }
}