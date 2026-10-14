package com.tonywww.jeioptimize.index;

import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy;
import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy.Capture;
import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy.Publication;
import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy.RedirectObservation;
import com.tonywww.jeioptimize.runtime.BudgetedIngredientFilterPolicy.Schedule;
import com.tonywww.jeioptimize.runtime.NativeFilterPublication;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Checks the budgeted-filter failure and tooltip decisions without Minecraft.
 * This does not start a client and does not measure startup time.
 */
public final class TooltipBudgetedPublicationTest {
    public static void main(String[] arguments) throws IOException {
        verify();
        System.out.println("TooltipBudgetedPublicationTest passed");
    }

    public static void verify() throws IOException {
        check(BudgetedIngredientFilterPolicy.publication(true, false, true) == Publication.PUBLISH,
            "a finished index for the current generation may be published");
        check(BudgetedIngredientFilterPolicy.publication(true, false, false) == Publication.ABORT_UNPUBLISHED,
            "an unfinished or failed index must not be published");
        check(BudgetedIngredientFilterPolicy.publication(true, true, true) == Publication.DISCARD_STALE_OR_CANCELLED,
            "a cancelled startup must not be published");
        check(BudgetedIngredientFilterPolicy.publication(false, false, true) == Publication.DISCARD_STALE_OR_CANCELLED,
            "a stale generation must not be published");
        check(BudgetedIngredientFilterPolicy.publication(false, true, false) == Publication.DISCARD_STALE_OR_CANCELLED,
            "a stale cancelled build must not be published");
        check(!BudgetedIngredientFilterPolicy.publishable(false, false, false), "an incomplete future is not publishable");
        check(!BudgetedIngredientFilterPolicy.publishable(true, true, false), "a cancelled future is not publishable");
        check(!BudgetedIngredientFilterPolicy.publishable(true, false, true), "a failed future is not publishable");
        check(BudgetedIngredientFilterPolicy.publishable(true, false, false), "a normally completed future is publishable");

        check(BudgetedIngredientFilterPolicy.rejectTooltipSearchIndex(true, true),
            "sorting-config plus tooltipSearchIndex is rejected");
        check(!BudgetedIngredientFilterPolicy.rejectTooltipSearchIndex(true, false),
            "sorting-config without tooltipSearchIndex stays on the budgeted path");
        check(!BudgetedIngredientFilterPolicy.rejectTooltipSearchIndex(false, true),
            "the comparator constructor keeps its tooltip path");

        check(BudgetedIngredientFilterPolicy.observe(false, false) == RedirectObservation.NOT_CONFIRMED,
            "a contract match without the redirect is not confirmation that the optimization ran");
        check(BudgetedIngredientFilterPolicy.observe(false, true) == RedirectObservation.NOT_CONFIRMED,
            "a capture without the redirect is still unconfirmed");
        check(BudgetedIngredientFilterPolicy.observe(true, false) == RedirectObservation.RAN_WITHOUT_BUDGETED_CAPTURE,
            "a redirect that keeps JEI's own search did not schedule a budgeted build");
        check(BudgetedIngredientFilterPolicy.observe(true, true) == RedirectObservation.BUDGETED_BUILD_SCHEDULED,
            "a redirect that captured an empty search scheduled the budgeted build");
        verifySortingConfigDoesNotCaptureWithoutAJob();
        verifyNativePublicationFailureDoesNotPublish();
        verifyNativePublicationCancellationDoesNotPublish();
        verifyNativePublicationSuccessPublishesOnce();

        ClassNode mixin = mixinClass();
        check(invokes(mixin, "captureChoice"), "the redirect uses the tested capture decision");
        check(invokes(mixin, "scheduleChoice"), "the return inject uses the tested schedule decision");
        check(invokesOwner(bootstrapClass(), "com/tonywww/jeioptimize/runtime/NativeFilterPublication", "bind"),
            "scheduleNative publishes through the tested completion binding");
        check(invokes(mixin, "rejectTooltipSearchIndex"), "the mixin asks before using the tooltip index");
        check(invokes(mixin, "observe"), "the mixin distinguishes a contract match from a redirect hit");
        String bytecode = new String(classBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
        check(bytecode.contains("empty or partial index will not be published"),
            "a real build failure logs that the index will not be published");
        check(bytecode.contains("createElementSearch redirect did not run"),
            "a missed redirect is logged instead of looking like an active optimization");
        check(bytecode.contains("tooltip search index rejected on sorting-config constructor"),
            "the rejected tooltip combination is logged");
        check(bytecode.contains("redirect confirmed: branch="),
            "a captured redirect logs confirmation separately from the contract check");
        check(bytecode.contains("no budgeted build was captured"),
            "a redirect that does not capture a build is logged");
        try (InputStream plugin = TooltipBudgetedPublicationTest.class.getResourceAsStream(
            "/com/tonywww/jeioptimize/JeiOptMixinPlugin.class")) {
            check(plugin != null, "mixin plugin exists");
            String pluginBytecode = new String(plugin.readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
            check(pluginBytecode.contains("contract check only"),
                "the constructor gate says it is only a contract check");
        }
    }

    private static void verifySortingConfigDoesNotCaptureWithoutAJob() {
        boolean[] bits = {false, true};
        for (boolean lowMemory : bits) {
            for (boolean canDeferFilter : bits) {
                for (boolean jeiStartThread : bits) {
                    for (boolean asyncOrDeferred : bits) {
                        for (boolean canDeferTooltip : bits) {
                            for (boolean tooltipMetrics : bits) {
                                for (boolean sortingConfig : bits) {
                                    Capture capture = BudgetedIngredientFilterPolicy.captureChoice(
                                        lowMemory, canDeferFilter, jeiStartThread, asyncOrDeferred, canDeferTooltip, tooltipMetrics);
                                    boolean captured = capture == Capture.EMPTY || capture == Capture.EMPTY_WITH_STORAGES;
                                    Schedule schedule = BudgetedIngredientFilterPolicy.scheduleChoice(
                                        sortingConfig, captured, canDeferTooltip, canDeferFilter, asyncOrDeferred);
                                    if (captured) {
                                        check(schedule == Schedule.TOOLTIP || schedule == Schedule.NATIVE
                                                || schedule == Schedule.BUDGETED_AWAIT,
                                            "a captured empty search must have a build job");
                                    } else {
                                        check(schedule == Schedule.NO_CAPTURE,
                                            "a full native search must not be given a second build job");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        for (boolean jeiStartThread : bits) {
            for (boolean tooltipWouldDefer : bits) {
                Capture capture = BudgetedIngredientFilterPolicy.captureChoice(
                    false, false, jeiStartThread, false, tooltipWouldDefer, false);
                check(capture == Capture.FULL_NATIVE,
                    "sorting-config with both ingredient-filter flags off keeps JEI's full search");
            }
        }
    }

    private static void verifyNativePublicationFailureDoesNotPublish() {
        CompletableFuture<String> build = new CompletableFuture<>();
        CompletableFuture<String> ready = new CompletableFuture<>();
        AtomicInteger publications = new AtomicInteger();
        AtomicInteger discards = new AtomicInteger();
        CompletableFuture<Void> published = NativeFilterPublication.bind(
            build, ready, search -> publications.incrementAndGet(), () -> discards.incrementAndGet());
        ready.completeExceptionally(new IllegalStateException("native slice failed"));
        check(publications.get() == 0, "a failed native build must not publish");
        check(published.isCompletedExceptionally(), "a failed native build must not finish as success");
        check(build.isCancelled(), "a failed native build must cancel the in-flight future");
        check(discards.get() == 1, "a failed native build must discard captured storages");
        try {
            published.join();
            throw new AssertionError("joining a failed native publication must throw");
        } catch (java.util.concurrent.CompletionException expected) {
            check(expected.getCause() instanceof IllegalStateException, "the native failure cause must be kept");
        }
    }

    private static void verifyNativePublicationCancellationDoesNotPublish() {
        CompletableFuture<String> build = new CompletableFuture<>();
        CompletableFuture<String> ready = new CompletableFuture<>();
        AtomicInteger publications = new AtomicInteger();
        CompletableFuture<Void> published = NativeFilterPublication.bind(
            build, ready, search -> publications.incrementAndGet(), () -> { });
        ready.cancel(false);
        check(publications.get() == 0, "a cancelled native build must not publish");
        check(published.isCompletedExceptionally(), "a cancelled native build must not finish as success");
    }

    private static void verifyNativePublicationSuccessPublishesOnce() {
        CompletableFuture<String> build = new CompletableFuture<>();
        CompletableFuture<String> ready = new CompletableFuture<>();
        AtomicReference<String> publishedSearch = new AtomicReference<>();
        AtomicInteger discards = new AtomicInteger();
        CompletableFuture<Void> published = NativeFilterPublication.bind(
            build, ready, publishedSearch::set, () -> discards.incrementAndGet());
        ready.complete("filled-index");
        check("filled-index".equals(publishedSearch.get()), "a completed native index is published");
        check(!published.isCompletedExceptionally(), "a completed native index finishes successfully");
        published.join();
        check(!build.isCancelled(), "success must not cancel the build");
        check(discards.get() == 0, "success must not discard the published storages");
    }

    private static ClassNode mixinClass() throws IOException {
        ClassNode mixin = new ClassNode();
        new ClassReader(classBytes()).accept(mixin, 0);
        return mixin;
    }

    private static byte[] classBytes() throws IOException {
        try (InputStream source = TooltipBudgetedPublicationTest.class.getResourceAsStream(
            "/com/tonywww/jeioptimize/mixin/IngredientFilterModernMixin.class")) {
            check(source != null, "modern ingredient filter mixin exists");
            return source.readAllBytes();
        }
    }

    private static ClassNode bootstrapClass() throws IOException {
        try (InputStream source = TooltipBudgetedPublicationTest.class.getResourceAsStream(
            "/com/tonywww/jeioptimize/runtime/JeiOptFilterBootstrap.class")) {
            check(source != null, "filter bootstrap exists");
            ClassNode bootstrap = new ClassNode();
            new ClassReader(source).accept(bootstrap, 0);
            return bootstrap;
        }
    }

    private static boolean invokesOwner(ClassNode type, String owner, String methodName) {
        for (MethodNode method : type.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(methodName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean invokes(ClassNode mixin, String methodName) {
        for (MethodNode method : mixin.methods) {
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                    && call.owner.equals("com/tonywww/jeioptimize/runtime/BudgetedIngredientFilterPolicy")
                    && call.name.equals(methodName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
