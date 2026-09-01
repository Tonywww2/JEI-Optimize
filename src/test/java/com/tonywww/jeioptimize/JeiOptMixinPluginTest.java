package com.tonywww.jeioptimize;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JeiOptMixinPluginTest {
    private static final String PLATFORM_HELPER = "mezz/jei/common/platform/IPlatformRecipeHelper";

    @Test
    void detectsForgeLegacyDisenchantInvocation() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        );

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.LEGACY,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void detectsNeoForgeLegacyDisenchantInvocation() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Holder;)Z"
        );

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.LEGACY,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void detectsModernCanEnchantHelper() {
        ClassNode target = classWithModernHelper();

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.MODERN,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsMixedModernAndLegacyCallGraph() {
        ClassNode target = classWithInvocation(
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            "(Lnet/minecraft/world/item/ItemStack;"
                + "Lnet/minecraft/world/item/enchantment/Enchantment;)Z"
        );
        addModernHelper(target, false);

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsUncalledModernHelper() {
        ClassNode target = new ClassNode();
        addModernHelper(target, true);

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    @Test
    void rejectsMethodWithoutSupportedHelperOrInvocation() {
        ClassNode target = new ClassNode();
        target.methods.add(new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            "()Ljava/util/stream/Stream;",
            null,
            null
        ));

        assertEquals(
            JeiOptMixinPlugin.GrindstoneVariant.NONE,
            JeiOptMixinPlugin.detectGrindstoneVariant(target)
        );
    }

    private static ClassNode classWithInvocation(String methodDescriptor, String invocationDescriptor) {
        ClassNode target = new ClassNode();
        MethodNode method = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            methodDescriptor,
            null,
            null
        );
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKEINTERFACE,
            PLATFORM_HELPER,
            "isItemEnchantable",
            invocationDescriptor,
            true
        ));
        target.methods.add(method);
        return target;
    }

    private static ClassNode classWithModernHelper() {
        ClassNode target = new ClassNode();
        addModernHelper(target, false);
        return target;
    }

    private static void addModernHelper(ClassNode target, boolean omitCaller) {
        String helperDescriptor = "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
            + "Lnet/minecraft/world/item/ItemStack;"
            + "Lnet/minecraft/world/item/enchantment/Enchantment;"
            + "Lnet/minecraft/resources/ResourceLocation;)Z";
        target.methods.add(new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "canEnchant",
            helperDescriptor,
            null,
            null
        ));
        if (omitCaller) {
            return;
        }
        MethodNode caller = new MethodNode(
            Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
            "getDisenchantRecipes",
            "(Lmezz/jei/common/platform/IPlatformRecipeHelper;"
                + "Lnet/minecraft/world/inventory/GrindstoneMenu;)Ljava/util/stream/Stream;",
            null,
            null
        );
        caller.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            "mezz/jei/library/plugins/vanilla/grindstone/GrindstoneRecipeMaker",
            "canEnchant",
            helperDescriptor,
            false
        ));
        target.methods.add(caller);
    }
}