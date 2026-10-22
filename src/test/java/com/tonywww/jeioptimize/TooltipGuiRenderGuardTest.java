package com.tonywww.jeioptimize;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;

public final class TooltipGuiRenderGuardTest {
    private static final String SCREEN = "Lnet/minecraft/client/gui/screens/Screen;";
    private static final String GRAPHICS = "Lnet/minecraft/client/gui/GuiGraphics;";
    private static final String POST = "onDrawScreenPost(" + SCREEN + GRAPHICS + "II)V";
    private static final String PREP = "updateForScreenRender(" + SCREEN + "II)V";
    private static final String DRAW_BG = "drawForScreenBackground(" + SCREEN + GRAPHICS + ")V";
    private static final String DRAW_FG = "drawForScreenForeground(" + SCREEN + GRAPHICS + "II)V";
    private static final String INIT = "onGuiInit(" + SCREEN + ")V";
    private static final String OPEN = "onGuiOpen(" + SCREEN + ")V";

    public static void main(String[] arguments) throws IOException {
        verifySyntheticContracts();
        verifyLegacyMixinCoverage();
        verifyModernMixinCoverage();
        verifyLifecycle();
        for (String argument : arguments) {
            if (argument.contains("=")) {
                continue;
            }
            verifyReleasedHandler(argument);
        }
        System.out.println("TooltipGuiRenderGuardTest passed");
    }

    private static void verifySyntheticContracts() {
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(null), "missing handler rejected");
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(null), "missing modern handler rejected");

        ClassNode empty = new ClassNode();
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(empty), "empty handler is not legacy");
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(empty), "empty handler is not modern");

        ClassNode initOnly = handlerWith(INIT);
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(initOnly), "onGuiInit alone is not a render-guard ABI");
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(initOnly), "onGuiInit alone is not the 19.57 ABI");

        ClassNode prepOnly = handlerWith(PREP);
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(prepOnly), "updateForScreenRender alone is not the 19.57 ABI");
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(prepOnly), "preparation without onDrawScreenPost is not legacy");

        ClassNode legacy = handlerWith(POST, INIT, OPEN);
        check(JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(legacy), "19.27-style screen-post ABI");
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(legacy), "screen-post ABI is not the 19.57 contract");

        ClassNode legacyWithPrep = handlerWith(POST, INIT, OPEN, PREP);
        check(JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(legacyWithPrep), "15.62 keeps the screen-post contract");
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(legacyWithPrep), "15.62 without drawForScreen* is not modern");

        ClassNode incompleteModern = handlerWith(PREP, DRAW_BG, INIT, OPEN);
        check(!JeiOptMixinPlugin.hasModernGuiRenderGuardContract(incompleteModern), "missing drawForScreenForeground rejects 19.57 mixin");
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(incompleteModern), "incomplete 19.57 is not legacy");

        ClassNode modern = handlerWith(PREP, DRAW_BG, DRAW_FG, INIT, OPEN);
        check(JeiOptMixinPlugin.hasModernGuiRenderGuardContract(modern), "complete 19.57 screen-render ABI");
        check(!JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(modern), "19.57 without onDrawScreenPost is not legacy");
    }

    private static void verifyLegacyMixinCoverage() throws IOException {
        ClassNode guard = loadMixin("JeiGuiRenderGuardMixin");
        checkInject(guard, "jeiOptimize$skipJeiInitUntilRuntimeReady", List.of(INIT), true);
        checkInject(guard, "jeiOptimize$skipJeiOpenUntilRuntimeReady", List.of(OPEN), true);
        checkInject(guard, "jeiOptimize$skipJeiRenderPreparationUntilRuntimeReady", List.of(PREP), false);
        checkInject(guard, "jeiOptimize$skipJeiRenderingUntilRuntimeReady", List.of(POST), true);
    }

    private static void verifyModernMixinCoverage() throws IOException {
        ClassNode guard = loadMixin("JeiGuiRenderGuardModernMixin");
        checkInject(guard, "jeiOptimize$skipJeiInitUntilRuntimeReady", List.of(INIT), true);
        checkInject(guard, "jeiOptimize$skipJeiOpenUntilRuntimeReady", List.of(OPEN), true);
        checkInject(guard, "jeiOptimize$skipJeiRenderPreparationUntilRuntimeReady", List.of(PREP), true);
        checkInject(guard, "jeiOptimize$skipJeiBackgroundUntilRuntimeReady", List.of(DRAW_BG), true);
        checkInject(guard, "jeiOptimize$skipJeiForegroundUntilRuntimeReady", List.of(DRAW_FG), true);
        check(guard.methods.stream().noneMatch(method -> method.name.contains("onDrawScreenPost")
                || injectsContain(method, POST)),
            "19.57 mixin must not target the removed onDrawScreenPost callback");
    }

    private static void verifyLifecycle() {
        long generation = 11L;
        JeiOptStartupProgressState.begin(generation);
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "preparing blocks without runtime");
        check(JeiOptStartupProgressState.blocksJeiRendering(true), "preparing blocks even if a runtime object appears early");

        check(JeiOptStartupProgressState.registerBuild(generation, 2, 40), "index build registered");
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "indexing blocks without runtime");

        JeiOptStartupProgressState.markChunkCompleted(generation);
        JeiOptStartupProgressState.markChunkCompleted(generation);
        JeiOptStartupProgressState.markReady(generation);
        check(JeiOptStartupProgressState.blocksJeiRendering(true), "ready index still blocks GUI until runtime publication");

        JeiOptStartupProgressState.markPublished(generation);
        check(JeiOptStartupProgressState.snapshot().stage() == JeiOptStartupProgressState.Stage.PUBLISHED,
            "filter publication is not runtime publication");
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "filter-published overlay still blocked without runtime");
        check(JeiOptStartupProgressState.blocksJeiRendering(true),
            "filter-published overlay stays blocked until JeiStarter finishes publishing");

        JeiOptStartupProgressState.markRuntimeComplete(generation);
        check(JeiOptStartupProgressState.snapshot().stage() == JeiOptStartupProgressState.Stage.HIDDEN,
            "runtime completion hides the startup overlay");
        check(!JeiOptStartupProgressState.blocksJeiRendering(true), "published runtime resumes GUI rendering");
        check(JeiOptStartupProgressState.blocksJeiRendering(false), "missing runtime still blocks after the overlay hides");

        long beforeFilter = 12L;
        JeiOptStartupProgressState.begin(beforeFilter);
        check(JeiOptStartupProgressState.registerBuild(beforeFilter, 1, 8), "second generation registered");
        JeiOptStartupProgressState.markRuntimeComplete(beforeFilter);
        check(JeiOptStartupProgressState.blocksJeiRendering(true), "runtime complete still waits for filter publication");
        JeiOptStartupProgressState.markReady(beforeFilter);
        JeiOptStartupProgressState.markPublished(beforeFilter);
        check(!JeiOptStartupProgressState.blocksJeiRendering(true), "both publications resume rendering");

        JeiOptStartupProgressState.cancel(beforeFilter);
    }

    private static void verifyReleasedHandler(String argument) throws IOException {
        Path path = Path.of(argument);
        try (ZipFile archive = new ZipFile(path.toFile())) {
            var entry = archive.getEntry("mezz/jei/gui/events/GuiEventHandler.class");
            if (entry == null) {
                return;
            }
            ClassNode handler = new ClassNode();
            try (InputStream source = archive.getInputStream(entry)) {
                new ClassReader(source).accept(handler, 0);
            }
            boolean legacy = JeiOptMixinPlugin.hasLegacyGuiRenderGuardContract(handler);
            boolean modern = JeiOptMixinPlugin.hasModernGuiRenderGuardContract(handler);
            String name = path.getFileName().toString();
            if (name.contains("19.57.")) {
                check(modern, "JEI 19.57 selects the modern render-guard mixin: " + name);
                check(!legacy, "JEI 19.57 must not select the onDrawScreenPost mixin: " + name);
                check(hasMethod(handler, PREP) && hasMethod(handler, DRAW_BG) && hasMethod(handler, DRAW_FG),
                    "JEI 19.57 keeps the crash-path callbacks: " + name);
            } else if (name.contains("19.27.")) {
                check(legacy, "JEI 19.27 selects the screen-post render-guard mixin: " + name);
                check(!modern, "JEI 19.27 must not select the 19.57 mixin: " + name);
                check(hasMethod(handler, POST), "JEI 19.27 keeps onDrawScreenPost: " + name);
            } else if (name.contains("15.62.")) {
                check(legacy, "JEI 15.62 selects the screen-post render-guard mixin: " + name);
                check(!modern, "JEI 15.62 must not select the 19.57 mixin: " + name);
                check(hasMethod(handler, POST) && hasMethod(handler, PREP),
                    "JEI 15.62 keeps post-draw and render-preparation callbacks: " + name);
            }
            System.out.println("GUI render-guard ABI: " + name + " legacy=" + legacy + " modern=" + modern);
        }
    }

    private static ClassNode handlerWith(String... selectors) {
        ClassNode handler = new ClassNode();
        for (String selector : selectors) {
            int split = selector.indexOf('(');
            MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, selector.substring(0, split),
                selector.substring(split), null, null);
            handler.methods.add(method);
        }
        return handler;
    }

    private static ClassNode loadMixin(String simpleName) throws IOException {
        try (InputStream source = TooltipGuiRenderGuardTest.class.getResourceAsStream(
            "/com/tonywww/jeioptimize/mixin/" + simpleName + ".class")) {
            check(source != null, simpleName + " class exists");
            ClassNode node = new ClassNode();
            new ClassReader(source).accept(node, 0);
            return node;
        }
    }

    private static void checkInject(ClassNode mixin, String handler, List<String> methods, boolean requireExact) {
        MethodNode method = mixin.methods.stream().filter(candidate -> candidate.name.equals(handler))
            .findFirst().orElseThrow(() -> new AssertionError("missing " + handler));
        AnnotationNode inject = method.visibleAnnotations.stream()
            .filter(annotation -> annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))
            .findFirst().orElseThrow(() -> new AssertionError(handler + " is not an @Inject"));
        boolean cancellable = false;
        boolean atHead = false;
        boolean required = false;
        List<?> targets = List.of();
        for (int index = 0; index < inject.values.size(); index += 2) {
            String key = (String) inject.values.get(index);
            Object value = inject.values.get(index + 1);
            switch (key) {
                case "method" -> targets = (List<?>) value;
                case "cancellable" -> cancellable = Boolean.TRUE.equals(value);
                case "require" -> required = Integer.valueOf(1).equals(value);
                case "at" -> {
                    AnnotationNode at = (AnnotationNode) ((List<?>) value).get(0);
                    atHead = at.values.contains("HEAD");
                }
                default -> {
                }
            }
        }
        check(cancellable && atHead, handler + " must cancel at HEAD");
        check(targets.containsAll(methods) && methods.containsAll(targets), handler + " targets " + methods);
        if (requireExact) {
            check(required, handler + " must require its verified ABI");
        }
        checkLifecycleGuard(mixin, handler);
    }

    private static void checkLifecycleGuard(ClassNode mixin, String handler) {
        MethodNode method = mixin.methods.stream().filter(candidate -> candidate.name.equals(handler))
            .findFirst().orElseThrow();
        boolean checksRuntime = false;
        boolean checksLifecycle = false;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                checksRuntime |= call.owner.equals("com/tonywww/jeioptimize/mixin/accessor/JeiRuntimeAccessor")
                    && call.name.equals("jeiopt$getNullableRuntime");
                checksLifecycle |= call.name.equals("blocksJeiRendering") && call.desc.equals("(Z)Z");
            }
        }
        check(checksRuntime && checksLifecycle, handler + " checks runtime presence and startup lifecycle");
    }

    private static boolean injectsContain(MethodNode method, String selector) {
        if (method.visibleAnnotations == null) {
            return false;
        }
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;") || annotation.values == null) {
                continue;
            }
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if ("method".equals(annotation.values.get(index))) {
                    return ((List<?>) annotation.values.get(index + 1)).contains(selector);
                }
            }
        }
        return false;
    }

    private static boolean hasMethod(ClassNode handler, String selector) {
        int split = selector.indexOf('(');
        String name = selector.substring(0, split);
        String desc = selector.substring(split);
        return handler.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(desc));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
