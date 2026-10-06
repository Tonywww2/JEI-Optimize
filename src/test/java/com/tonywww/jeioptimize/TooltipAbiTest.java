package com.tonywww.jeioptimize;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public final class TooltipAbiTest {
    public static void main(String[] arguments) throws IOException {
        verifyPluginCallbackRoutingContract();
        verifyRenderPreparationGuard();
        verifyNativeSearchBuilderContract();
        verifyModernIngredientFilterConstructorContract();
        verifyMineColoniesContracts();
        verifyMineColoniesAttributeContract();
        verifyIronsSpellsContracts();
        ClassNode grid = new ClassNode();
        check(!JeiOptMixinPlugin.hasStartupGridRefreshContract(grid), "missing grid listener");
        MethodNode listener = new MethodNode(Opcodes.ACC_PRIVATE, "lambda$new$0", "()V", null, null);
        grid.methods.add(listener);
        String gridOwner = "mezz/jei/gui/overlay/ingredients/IngredientGridWithNavigation";
        listener.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, gridOwner, "getPageAnchorElement",
            "()Lmezz/jei/gui/overlay/elements/IElement;", false));
        check(!JeiOptMixinPlugin.hasStartupGridRefreshContract(grid), "unrelated lambda rejected");
        listener.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, gridOwner, "updateLayoutKeepingPageAnchorVisible",
            "(Lmezz/jei/gui/overlay/elements/IElement;)V", false));
        check(JeiOptMixinPlugin.hasStartupGridRefreshContract(grid), "verified grid-only listener");
        ClassNode reload = new ClassNode();
        check(!JeiOptMixinPlugin.hasTooltipReloadContract(reload), "missing reload hook");
        reload.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "m_6213_",
            "(Lnet/minecraft/server/packs/resources/ResourceManager;)V", null, null));
        check(JeiOptMixinPlugin.hasTooltipReloadContract(reload), "production Forge reload hook");
        reload.methods.get(0).name = "onResourceManagerReload";
        check(JeiOptMixinPlugin.hasTooltipReloadContract(reload), "named reload hook");
        reload.methods.get(0).desc = "()V";
        check(!JeiOptMixinPlugin.hasTooltipReloadContract(reload), "reject changed reload descriptor");
        check(JeiOptMixinPlugin.detectTooltipPrefix(new ClassNode()) == 0, "missing constructor");
        ClassNode parser = fixture('#', "getTagSearchMode");
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == 0, "tag is not tooltip");
        parser = fixture('#', "getTooltipSearchMode");
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == '#', "legacy prefix");
        parser = fixture('$', "getTooltipSearchMode");
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == '$', "modern prefix");
        parser.methods.add(fixture('#', "getTooltipSearchMode").methods.get(0));
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == 0, "ambiguous prefix");
        parser = fixture(0, "getTooltipSearchMode");
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == 0, "invalid prefix");
        parser = fixture('#', "getTooltipSearchMode");
        parser.methods.get(0).instructions.insertBefore(parser.methods.get(0).instructions.getLast(),
            new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "Parser", "addPrefix", "()V", false));
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == 0, "do not reuse prior prefix");
        ClassNode config = new ClassNode();
        check(!JeiOptMixinPlugin.hasTooltipSettingsContract(config), "missing advanced setting");
        config.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "searchAdvancedTooltips",
            "()Lnet/mezzdev/config/api/value/IConfigValue;", null, null));
        check(JeiOptMixinPlugin.hasTooltipSettingsContract(config), "MezzConfig setting");
        check(!JeiOptMixinPlugin.hasTooltipLowMemoryContract(config), "advanced is not low-memory");
        config.methods.add(new MethodNode(Opcodes.ACC_PUBLIC, "lowMemorySlowSearchEnabled",
            "()Lnet/mezzdev/config/api/value/IConfigValue;", null, null));
        check(JeiOptMixinPlugin.hasTooltipLowMemoryContract(config), "MezzConfig low-memory protection");
        parser = configValueFixture("tooltipSearchMode");
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == '$', "MezzConfig tooltip prefix");
        check(JeiOptMixinPlugin.detectTooltipPrefix(configValueFixture("tagSearchMode")) == 0, "config value is not enough");
        parser.methods.add(configValueFixture("tooltipSearchMode").methods.get(0));
        check(JeiOptMixinPlugin.detectTooltipPrefix(parser) == 0, "ambiguous MezzConfig prefix");

        for (String argument : arguments) {
            if (verifyCompatibilityArchive(argument)) {
                continue;
            }
            try (ZipFile archive = new ZipFile(argument)) {
                var callerEntry = archive.getEntry("mezz/jei/library/load/PluginCaller.class");
                if (callerEntry != null) {
                    try (InputStream source = archive.getInputStream(callerEntry)) {
                        ClassNode caller = new ClassNode();
                        new ClassReader(source).accept(caller, 0);
                        check(JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller),
                            "released callback argument routing " + argument);
                    }
                }
                var searchEntry = archive.getEntry("mezz/jei/gui/search/ElementSearch.class");
                if (searchEntry != null) {
                    try (InputStream source = archive.getInputStream(searchEntry)) {
                        ClassNode search = new ClassNode();
                        new ClassReader(source).accept(search, 0);
                        boolean supported = JeiOptMixinPlugin.hasNativeSearchBuilderContract(search);
                        boolean hasTargetConstructor = search.methods.stream().anyMatch(method -> method.name.equals("<init>")
                            && method.desc.equals("(Lmezz/jei/gui/search/ElementPrefixParser;)V"));
                        check(!supported || hasTargetConstructor, "released native builder requires its injection constructor");
                        if (argument.contains("19.56.") || argument.contains("19.57.")) {
                            check(!supported, "changed ElementSearch constructor disables native builder " + argument);
                        }
                        System.out.println("Native builder ABI: " + Path.of(argument).getFileName() + " supported=" + supported);
                    }
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/common/Internal.class"))) {
                    ClassNode internal = new ClassNode();
                    new ClassReader(source).accept(internal, 0);
                    check(JeiOptMixinPlugin.hasRuntimeAccessContract(internal), "released nullable runtime field " + argument);
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/gui/ingredients/IngredientFilter.class"))) {
                    ClassNode filter = new ClassNode();
                    new ClassReader(source).accept(filter, 0);
                    boolean supported = JeiOptMixinPlugin.hasModernIngredientFilterConstructorContract(filter);
                    if (argument.contains("19.56.") || argument.contains("19.57.")) {
                        check(!supported, "changed ingredient filter constructor retains native construction " + argument);
                    }
                    System.out.println("Ingredient filter constructor ABI: " + Path.of(argument).getFileName() + " supported=" + supported);
                }
                if (argument.contains("15.59.") || argument.contains("19.56.")) {
                    try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/gui/events/GuiEventHandler.class"))) {
                        ClassNode handler = new ClassNode();
                        new ClassReader(source).accept(handler, 0);
                        check(handler.methods.stream().anyMatch(method -> method.name.equals("updateForScreenRender")
                            && method.desc.equals("(Lnet/minecraft/client/gui/screens/Screen;II)V")), "released render preparation entry point");
                    }
                }
                var gridEntry = archive.getEntry("mezz/jei/gui/overlay/ingredients/IngredientGridWithNavigation.class");
                if (gridEntry != null) {
                    try (InputStream source = archive.getInputStream(gridEntry)) {
                        ClassNode realGrid = new ClassNode();
                        new ClassReader(source).accept(realGrid, 0);
                        boolean supported = JeiOptMixinPlugin.hasStartupGridRefreshContract(realGrid);
                        if (argument.contains("15.59.0.212")) {
                            check(supported, "ATM9 grid refresh contract");
                        }
                        System.out.println("Grid refresh ABI: " + Path.of(argument).getFileName() + " supported=" + supported);
                    }
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/common/config/IClientConfig.class"))) {
                    ClassNode realClientConfig = new ClassNode();
                    new ClassReader(source).accept(realClientConfig, 0);
                    check(JeiOptMixinPlugin.hasTooltipLowMemoryContract(realClientConfig), "released low-memory setting");
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/common/config/IIngredientFilterConfig.class"))) {
                    ClassNode realConfig = new ClassNode();
                    new ClassReader(source).accept(realConfig, 0);
                    check(JeiOptMixinPlugin.hasTooltipSettingsContract(realConfig), "released advanced setting");
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/gui/search/ElementPrefixParser.class"))) {
                    ClassNode realParser = new ClassNode();
                    new ClassReader(source).accept(realParser, 0);
                    char expected = argument.contains("15.20.") || argument.contains("15.21.") ? '#' : '$';
                    check(JeiOptMixinPlugin.detectTooltipPrefix(realParser) == expected, "released parser " + Path.of(argument).getFileName());
                    System.out.println("Tooltip ABI verified: " + Path.of(argument).getFileName() + " prefix=" + expected);
                }
                try (InputStream source = archive.getInputStream(archive.getEntry("mezz/jei/gui/startup/ResourceReloadHandler.class"))) {
                    ClassNode realReload = new ClassNode();
                    new ClassReader(source).accept(realReload, 0);
                    check(JeiOptMixinPlugin.hasTooltipReloadContract(realReload), "released reload " + Path.of(argument).getFileName());
                }
            }
        }
        System.out.println("TooltipAbiTest passed");
    }

    private static void verifyPluginCallbackRoutingContract() throws IOException {
        String descriptor = "(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)V";
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(null), "missing plugin caller rejected");
        ClassNode caller = new ClassNode();
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "missing callback method rejected");
        MethodNode callback = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            "callOnPlugins", descriptor, null, null);
        caller.methods.add(callback);
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "callback without code rejected");
        callback.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "PluginCaller",
            "emi$callOnPlugins", "(Ljava/util/function/Consumer;Ljava/lang/Object;Ljava/lang/String;"
                + "Ljava/util/List;Ljava/util/function/Consumer;)V", false));
        callback.instructions.add(new InsnNode(Opcodes.RETURN));
        check(JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller),
            "an EMI Redirect must not disable callback argument routing");
        callback.access = Opcodes.ACC_PUBLIC;
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "instance callback rejected");
        callback.access |= Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT;
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "abstract callback rejected");
        callback.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE;
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "native callback rejected");
        callback.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        callback.desc = "(Ljava/lang/String;Ljava/util/List;Ljava/util/function/Consumer;)Z";
        check(!JeiOptMixinPlugin.hasPluginCallbackRoutingContract(caller), "changed callback descriptor rejected");

        try (InputStream source = TooltipAbiTest.class.getResourceAsStream(
            "/com/tonywww/jeioptimize/mixin/PluginCallerMixin.class")) {
            check(source != null, "plugin caller mixin exists");
            ClassNode mixin = new ClassNode();
            new ClassReader(source).accept(mixin, 0);
            var handler = mixin.methods.stream().filter(method -> method.name.equals("jeiOptimize$wrapPluginCall"))
                .findFirst().orElseThrow();
            var modifier = handler.visibleAnnotations.stream().filter(annotation -> annotation.desc.equals(
                "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;")).findFirst().orElseThrow();
            check(modifier.values.get(modifier.values.indexOf("method") + 1)
                .equals(java.util.List.of("callOnPlugins" + descriptor)), "callback selector matches its ABI gate");
            check(Boolean.TRUE.equals(modifier.values.get(modifier.values.indexOf("argsOnly") + 1)),
                "route the callback argument without relying on local variable debug data");
            check(Integer.valueOf(0).equals(modifier.values.get(modifier.values.indexOf("ordinal") + 1)),
                "wrap the sole Consumer argument");
            var at = (org.objectweb.asm.tree.AnnotationNode) modifier.values.get(modifier.values.indexOf("at") + 1);
            check("HEAD".equals(at.values.get(at.values.indexOf("value") + 1)), "wrap before plugin filtering");
            check(mixin.methods.stream().filter(method -> method.visibleAnnotations != null)
                .flatMap(method -> method.visibleAnnotations.stream()).noneMatch(annotation -> annotation.desc.equals(
                    "Lorg/spongepowered/asm/mixin/injection/Redirect;")), "do not compete with EMI's Redirect");
        }
    }

    private static void verifyNativeSearchBuilderContract() throws IOException {
        String constructor = "(Lmezz/jei/gui/search/ElementPrefixParser;)V";
        check(!JeiOptMixinPlugin.hasNativeSearchBuilderContract(null), "missing native search class rejected");
        ClassNode search = new ClassNode();
        MethodNode oldConstructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", constructor, null, null);
        search.methods.add(oldConstructor);
        check(!JeiOptMixinPlugin.hasNativeSearchBuilderContract(search), "constructor without builder rejected");
        MethodNode newConstructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>",
            "(Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/Collection;Lmezz/jei/api/runtime/IIngredientManager;)V", null, null);
        search.methods.add(newConstructor);
        newConstructor.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "mezz/jei/api/search/ISearchStorageBuilder", "build", "()Lmezz/jei/api/search/ISearchStorage;", true));
        check(!JeiOptMixinPlugin.hasNativeSearchBuilderContract(search), "builder in different constructor rejected (issue #10)");
        oldConstructor.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
            "mezz/jei/api/search/ISearchStorageBuilder", "build", "()Lmezz/jei/api/search/ISearchStorage;", true));
        check(JeiOptMixinPlugin.hasNativeSearchBuilderContract(search), "builder in exact injection constructor accepted");
        search.methods.remove(oldConstructor);
        check(!JeiOptMixinPlugin.hasNativeSearchBuilderContract(search), "new constructor alone rejected");

        try (InputStream source = TooltipAbiTest.class.getResourceAsStream("/com/tonywww/jeioptimize/mixin/JeiNativeSearchBuilderMixin.class")) {
            check(source != null, "native search builder mixin exists");
            ClassNode mixin = new ClassNode();
            new ClassReader(source).accept(mixin, 0);
            MethodNode handler = mixin.methods.stream().filter(method -> method.name.equals("jeiopt$retainBulkBuilder"))
                .findFirst().orElseThrow();
            var wrap = handler.visibleAnnotations.stream()
                .filter(annotation -> annotation.desc.equals("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;"))
                .findFirst().orElseThrow();
            int methodIndex = wrap.values.indexOf("method");
            check(methodIndex >= 0 && wrap.values.get(methodIndex + 1).equals(java.util.List.of("<init>" + constructor)),
                "compiled injector and compatibility gate must use the same exact constructor");
        }
    }

    private static void verifyModernIngredientFilterConstructorContract() throws IOException {
        check(!JeiOptMixinPlugin.hasModernIngredientFilterConstructorContract(null), "missing filter class rejected");
        ClassNode filter = new ClassNode();
        filter.methods.add(new MethodNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "createElementSearch",
            "(Lmezz/jei/common/config/IClientConfig;Lmezz/jei/gui/search/ElementPrefixParser;Ljava/util/List;"
                + "Lmezz/jei/api/runtime/IIngredientManager;)Lmezz/jei/gui/search/IElementSearch;", null, null));
        check(!JeiOptMixinPlugin.hasModernIngredientFilterConstructorContract(filter), "factory alone cannot validate a constructor injection");
        try (InputStream source = TooltipAbiTest.class.getResourceAsStream("/com/tonywww/jeioptimize/mixin/IngredientFilterModernMixin.class")) {
            check(source != null, "modern ingredient filter mixin exists");
            ClassNode mixin = new ClassNode();
            new ClassReader(source).accept(mixin, 0);
            int handlers = 0;
            for (MethodNode handler : mixin.methods) {
                if (!handler.name.equals("jeiopt$deferElementSearch") && !handler.name.equals("jeiopt$scheduleAsyncBuild")) {
                    continue;
                }
                for (var annotation : handler.visibleAnnotations) {
                    int methodIndex = annotation.values == null ? -1 : annotation.values.indexOf("method");
                    if (methodIndex < 0) { continue; }
                    var selectors = (java.util.List<?>) annotation.values.get(methodIndex + 1);
                    check(selectors.size() == 1, "one exact filter constructor target");
                    String selector = (String) selectors.get(0);
                    check(selector.startsWith("<init>("), "filter injector must name a constructor descriptor explicitly");
                    ClassNode target = new ClassNode();
                    MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", selector.substring(6), null, null);
                    target.methods.add(constructor);
                    check(JeiOptMixinPlugin.hasModernIngredientFilterConstructorContract(target), "compiled filter injector matches its gate");
                    constructor.desc = "(Ljava/lang/Object;)V";
                    check(!JeiOptMixinPlugin.hasModernIngredientFilterConstructorContract(target), "changed filter constructor rejected");
                    handlers++;
                }
            }
            check(handlers == 2, "both filter constructor injections verified");
        }
    }

    private static void verifyRenderPreparationGuard() throws IOException {
        ClassNode internal = new ClassNode();
        check(!JeiOptMixinPlugin.hasRuntimeAccessContract(internal), "missing nullable runtime field rejected");
        FieldNode field = new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "jeiRuntime", "Lmezz/jei/api/runtime/IJeiRuntime;", null, null);
        internal.fields.add(field);
        check(JeiOptMixinPlugin.hasRuntimeAccessContract(internal), "nullable runtime field accepted");
        field.access = Opcodes.ACC_PRIVATE;
        check(!JeiOptMixinPlugin.hasRuntimeAccessContract(internal), "instance runtime field rejected");
        field.access |= Opcodes.ACC_STATIC;
        field.desc = "Ljava/lang/Object;";
        check(!JeiOptMixinPlugin.hasRuntimeAccessContract(internal), "changed field type rejected");
        try (InputStream source = TooltipAbiTest.class.getResourceAsStream("/com/tonywww/jeioptimize/mixin/JeiGuiRenderGuardMixin.class")) {
            check(source != null, "render guard class exists");
            ClassNode guard = new ClassNode();
            new ClassReader(source).accept(guard, 0);
            MethodNode preparation = guard.methods.stream()
                .filter(method -> method.name.equals("jeiOptimize$skipJeiRenderPreparationUntilRuntimeReady")).findFirst().orElseThrow();
            boolean targetsPreparation = false;
            boolean cancellable = false;
            boolean atHead = false;
            for (var annotation : preparation.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) { continue; }
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    String key = (String) annotation.values.get(index);
                    Object value = annotation.values.get(index + 1);
                    if (key.equals("method")) {
                        targetsPreparation = ((java.util.List<?>) value).contains("updateForScreenRender(Lnet/minecraft/client/gui/screens/Screen;II)V");
                    } else if (key.equals("cancellable")) {
                        cancellable = Boolean.TRUE.equals(value);
                    } else if (key.equals("at")) {
                        var at = (org.objectweb.asm.tree.AnnotationNode) ((java.util.List<?>) value).get(0);
                        atHead = at.values.contains("HEAD");
                    }
                }
            }
            check(targetsPreparation && cancellable && atHead, "render preparation must be guarded before layout construction");
            boolean checksRuntime = false;
            boolean checksLifecycle = false;
            for (var instruction : preparation.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    checksRuntime |= call.owner.equals("com/tonywww/jeioptimize/mixin/accessor/JeiRuntimeAccessor")
                        && call.name.equals("jeiopt$getNullableRuntime");
                    checksLifecycle |= call.name.equals("blocksJeiRendering") && call.desc.equals("(Z)Z");
                }
            }
            check(checksRuntime && checksLifecycle, "render preparation checks actual runtime and startup lifecycle");
        }
    }

    private static boolean verifyCompatibilityArchive(String argument) throws IOException {
        int separator = argument.indexOf('=');
        if (separator < 0) {
            return false;
        }
        String kind = argument.substring(0, separator);
        Path path = Path.of(argument.substring(separator + 1));
        Map<String, ClassNode> targets = new HashMap<>();
        try (ZipFile archive = new ZipFile(path.toFile())) {
            String prefix = switch (kind) {
                case "minecolonies" -> "com/minecolonies/";
                case "irons-supported", "irons-unsupported" -> "io/redspace/ironsspellbooks/";
                default -> throw new IllegalArgumentException("Unknown compatibility fixture: " + kind);
            };
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || !name.endsWith(".class")) {
                    continue;
                }
                try (InputStream source = archive.getInputStream(entry)) {
                    ClassNode target = new ClassNode();
                    new ClassReader(source).accept(target, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    targets.put(target.name.replace('/', '.'), target);
                }
            }
        }
        if (kind.equals("minecolonies")) {
            check(targets.containsKey("com.minecolonies.core.compatibility.jei.JEIPlugin"), "MineColonies fixture contains plugin");
            assertMineColonies(targets, true, false, "released core without Tweaks: " + path.getFileName());
            check(JeiOptMixinPlugin.hasMineColoniesAttributeContract(targets.get("com.minecolonies.api.util.ItemStackUtils")),
                "released MineColonies attribute repair: " + path.getFileName());
        } else {
            check(targets.containsKey("io.redspace.ironsspellbooks.jei.ArcaneAnvilRecipeMaker"), "Iron's Spells fixture contains maker");
            assertIronsSpells(targets, kind.equals("irons-supported"), "released recipe contract: " + path.getFileName());
        }
        System.out.println("Compatibility ABI verified: " + kind + " " + path.getFileName());
        return true;
    }

    private static void verifyMineColoniesAttributeContract() {
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(null), "missing attribute helper rejected");
        ClassNode target = new ClassNode();
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "getItemStackAttributeValue",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D", null, null);
        target.methods.add(method);
        MethodInsnNode collection = new MethodInsnNode(Opcodes.INVOKEINTERFACE, "com/google/common/collect/Multimap", "get",
            "(Ljava/lang/Object;)Ljava/util/Collection;", true);
        method.instructions.add(collection);
        MethodInsnNode iteration = new MethodInsnNode(Opcodes.INVOKEINTERFACE, "java/util/Collection", "forEach", "(Ljava/util/function/Consumer;)V", true);
        method.instructions.add(iteration);
        String instance = "net/minecraft/world/entity/ai/attributes/AttributeInstance";
        MethodInsnNode constructor = new MethodInsnNode(Opcodes.INVOKESPECIAL, instance, "<init>",
            "(Lnet/minecraft/world/entity/ai/attributes/Attribute;Ljava/util/function/Consumer;)V", false);
        method.instructions.insert(constructor);
        MethodInsnNode value = new MethodInsnNode(Opcodes.INVOKEVIRTUAL, instance, "getValue", "()D", false);
        method.instructions.add(value);
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "missing modifier consumer rejected");
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
        InvokeDynamicInsnNode consumer = new InvokeDynamicInsnNode("accept", "(L" + instance + ";)Ljava/util/function/Consumer;", bootstrap,
            new Handle(Opcodes.H_INVOKEVIRTUAL, instance, "addTransientModifier", "(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V", false));
        method.instructions.add(consumer);
        check(JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "named attribute contract");
        value.name = "m_22135_";
        consumer.bsmArgs[0] = new Handle(Opcodes.H_INVOKEVIRTUAL, instance, "m_22118_", "(Lnet/minecraft/world/entity/ai/attributes/AttributeModifier;)V", false);
        check(JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "production attribute contract");
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "com/google/common/collect/Multimap", "get", collection.desc, true));
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "ambiguous collection rejected");
        method.instructions.remove(method.instructions.getLast());
        method.instructions.remove(constructor);
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "missing temporary instance rejected");
        method.instructions.insert(constructor);
        method.access = Opcodes.ACC_PUBLIC;
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "nonstatic helper rejected");
        method.access |= Opcodes.ACC_STATIC;
        iteration.name = "unrecognized";
        check(!JeiOptMixinPlugin.hasMineColoniesAttributeContract(target), "unknown iteration rejected");
    }

    private static void verifyIronsSpellsContracts() {
        String root = "io.redspace.ironsspellbooks.";
        String recipe = root + "jei.ArcaneAnvilJeiRecipe";
        String tuple = recipe + "$Tuple";
        Map<String, ClassNode> targets = new HashMap<>();
        MethodNode maker = member(targets, root + "jei.ArcaneAnvilRecipeMaker", "getRecipes",
            "(Lmezz/jei/api/recipe/vanilla/IVanillaRecipeFactory;Lio/redspace/ironsspellbooks/jei/JeiPlugin$ItemFinder;)Ljava/util/List;");
        maker.access = Opcodes.ACC_STATIC;
        assertIronsSpells(targets, false, "old recipe class missing disables both patches");
        MethodNode items = member(targets, recipe, "getRecipeItems", "()Lio/redspace/ironsspellbooks/jei/ArcaneAnvilJeiRecipe$Tuple;");
        targets.get(recipe).fields.add(new FieldNode(0, "leftItem", "Lnet/minecraft/world/item/Item;", null, null));
        targets.get(recipe).fields.add(new FieldNode(0, "rightItem", "Lnet/minecraft/world/item/Item;", null, null));
        MethodNode constructor = member(targets, tuple, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
        MethodNode enabledSpells = member(targets, root + "api.registry.SpellRegistry", "getEnabledSpells", "()Ljava/util/List;");
        enabledSpells.access |= Opcodes.ACC_STATIC;
        member(targets, root + "api.spells.AbstractSpell", "getSpellId", "()Ljava/lang/String;");
        member(targets, root + "api.spells.AbstractSpell", "getMinLevel", "()I");
        member(targets, root + "api.spells.AbstractSpell", "getMaxLevel", "()I");
        MethodNode container = member(targets, root + "api.spells.ISpellContainer", "createScrollContainer",
            "(Lio/redspace/ironsspellbooks/api/spells/AbstractSpell;ILnet/minecraft/world/item/ItemStack;)Lio/redspace/ironsspellbooks/api/spells/ISpellContainer;");
        container.access |= Opcodes.ACC_STATIC;
        ClassNode registry = new ClassNode();
        targets.put(root + "registries.ItemRegistry", registry);
        FieldNode scroll = new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "SCROLL", "Lnet/minecraftforge/registries/RegistryObject;", null, null);
        registry.fields.add(scroll);
        assertIronsSpells(targets, true, "complete Forge contract");
        scroll.desc = "Lnet/neoforged/neoforge/registries/DeferredHolder;";
        assertIronsSpells(targets, true, "complete NeoForge contract");
        scroll.access = Opcodes.ACC_PUBLIC;
        assertIronsSpells(targets, false, "nonstatic scroll rejected");
        scroll.access |= Opcodes.ACC_STATIC;
        scroll.desc = "Ljava/lang/Object;";
        assertIronsSpells(targets, false, "unknown holder rejected");
        scroll.desc = "Lnet/minecraftforge/registries/RegistryObject;";
        enabledSpells.access = Opcodes.ACC_PUBLIC;
        assertIronsSpells(targets, false, "instance factory rejected");
        enabledSpells.access |= Opcodes.ACC_STATIC;
        container.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertIronsSpells(targets, false, "private reflection API rejected");
        container.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC;
        FieldNode leftItem = targets.get(recipe).fields.get(0);
        leftItem.access = Opcodes.ACC_STATIC;
        assertIronsSpells(targets, false, "static recipe state rejected");
        leftItem.access = 0;
        for (Map.Entry<String, ClassNode> entry : Map.copyOf(targets).entrySet()) {
            targets.remove(entry.getKey());
            assertIronsSpells(targets, false, "missing dependency: " + entry.getKey());
            targets.put(entry.getKey(), entry.getValue());
        }
        String oldDescriptor = items.desc;
        items.desc = "()Ljava/lang/Object;";
        assertIronsSpells(targets, false, "changed recipe return rejected");
        items.desc = oldDescriptor;
        constructor.desc = "(Ljava/util/List;Ljava/util/List;Ljava/util/List;)V";
        assertIronsSpells(targets, false, "changed tuple constructor rejected");
        constructor.desc = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V";
        maker.desc = "()Ljava/util/List;";
        assertIronsSpells(targets, false, "changed maker descriptor rejected");
    }

    private static void assertIronsSpells(Map<String, ClassNode> targets, boolean expected, String message) {
        for (String mixin : java.util.List.of("IronsSpellsArcaneAnvilMakerMixin", "IronsSpellsArcaneAnvilRecipeMixin")) {
            check(JeiOptMixinPlugin.hasCompatibilityContract("compat." + mixin, targets::get) == expected, message + ": " + mixin);
        }
    }

    private static void verifyMineColoniesContracts() {
        String root = "com.minecolonies.";
        String plugin = root + "core.compatibility.jei.JEIPlugin";
        String analyzer = root + "core.colony.crafting.ToolsAnalyzer";
        String equipment = root + "api.equipment.registry.EquipmentTypeEntry";
        String extension = "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeExtension";
        String tags = "steve_gall.minecolonies_tweaks.api.common.tool.ToolTypeTags";
        String stack = "Lnet/minecraft/world/item/ItemStack;";
        Map<String, ClassNode> targets = new HashMap<>();
        MethodNode registration = member(targets, plugin, "registerRecipes", "(Lmezz/jei/api/registration/IRecipeRegistration;)V");
        registration.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/minecolonies/core/compatibility/jei/ToolRecipeCategory", "findRecipes", "()Ljava/util/List;", false));
        MethodNode find = member(targets, analyzer, "findTools", "()Ljava/util/List;");
        find.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, equipment.replace('.', '/'), "checkIsEquipment", "(" + stack + ")Z", false));
        MethodNode add = member(targets, analyzer, "tryAddingToolWithLevel", "(Ljava/util/Map;Lcom/minecolonies/api/equipment/registry/EquipmentTypeEntry;" + stack + ")V");
        add.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, equipment.replace('.', '/'), "getMiningLevel", "(" + stack + ")I", false));
        member(targets, equipment, "checkIsEquipment", "(" + stack + ")Z");
        member(targets, equipment, "getMiningLevel", "(" + stack + ")I");
        assertMineColonies(targets, true, false, "without optional Tweaks");
        member(targets, extension, "isCustomTool", "(" + stack + ")Z");
        member(targets, extension, "getTagLevel", "(" + stack + ")I");
        member(targets, extension, "getCustomLevel", "(" + stack + ")I");
        assertMineColonies(targets, true, false, "partial Tweaks rejected independently");
        MethodNode blacklist = member(targets, tags, "isInBlacklist", "(" + stack + "Lnet/minecraft/resources/ResourceLocation;)Z");
        assertMineColonies(targets, true, true, "complete Tweaks");
        blacklist.desc = "()Z";
        assertMineColonies(targets, true, false, "changed Tweaks descriptor");
        targets.remove(equipment);
        assertMineColonies(targets, false, false, "missing core rejects all");
        member(targets, equipment, "checkIsEquipment", "(" + stack + ")Z");
        member(targets, equipment, "getMiningLevel", "(" + stack + ")I");
        find.instructions.clear();
        assertMineColonies(targets, false, false, "changed core call graph");
    }

    private static void assertMineColonies(Map<String, ClassNode> targets, boolean core, boolean tweaks, String message) {
        for (String mixin : java.util.List.of("MineColoniesJeiPluginMixin", "MineColoniesEquipmentTypeEntryMixin")) {
            check(JeiOptMixinPlugin.hasCompatibilityContract("compat." + mixin, targets::get) == core, message + ": " + mixin);
        }
        for (String mixin : java.util.List.of("MineColoniesTweaksToolTypeExtensionMixin", "MineColoniesTweaksToolTypeTagsMixin")) {
            check(JeiOptMixinPlugin.hasCompatibilityContract("compat." + mixin, targets::get) == tweaks, message + ": " + mixin);
        }
    }

    private static MethodNode member(Map<String, ClassNode> targets, String owner, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        targets.computeIfAbsent(owner, ignored -> new ClassNode()).methods.add(method);
        return method;
    }

    private static ClassNode fixture(int prefix, String modeMethod) {
        ClassNode parser = new ClassNode();
        MethodNode constructor = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        constructor.instructions.add(new IntInsnNode(Opcodes.BIPUSH, prefix));
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
        Handle mode = new Handle(Opcodes.H_INVOKEINTERFACE, "mezz/jei/common/config/IIngredientFilterConfig", modeMethod, "()Ljava/lang/Object;", true);
        constructor.instructions.add(new InvokeDynamicInsnNode("getMode", "()Ljava/lang/Object;", bootstrap, mode));
        parser.methods.add(constructor);
        return parser;
    }

    private static ClassNode configValueFixture(String setting) {
        ClassNode parser = fixture('$', "unused");
        var instructions = parser.methods.get(0).instructions;
        instructions.remove(instructions.getLast());
        instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, "mezz/jei/common/config/IIngredientFilterConfig",
            setting, "()Lnet/mezzdev/config/api/value/IConfigValue;", true));
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory", "()V", false);
        Handle get = new Handle(Opcodes.H_INVOKEINTERFACE, "net/mezzdev/config/api/value/IConfigValue", "get", "()Ljava/lang/Object;", true);
        instructions.add(new InvokeDynamicInsnNode("getMode",
            "(Lnet/mezzdev/config/api/value/IConfigValue;)Lmezz/jei/common/search/PrefixInfo$IModeGetter;", bootstrap, get));
        return parser;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
