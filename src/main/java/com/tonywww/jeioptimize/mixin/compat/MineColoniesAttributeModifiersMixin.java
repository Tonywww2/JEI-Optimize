package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.instrumentation.JeiPluginCallContext;
import com.tonywww.jeioptimize.integration.MineColoniesAttributeModifiers;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Collection;
import java.util.function.Consumer;

@Pseudo
@Mixin(targets = "com.minecolonies.api.util.ItemStackUtils", remap = false)
public abstract class MineColoniesAttributeModifiersMixin {
    @WrapOperation(
        method = "getItemStackAttributeValue(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/ai/attributes/Attribute;)D",
        at = @At(value = "INVOKE", target = "Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V"),
        require = 1
    )
    private static void jeiopt$applyEquipmentModifiers(Collection<AttributeModifier> modifiers,
        Consumer<AttributeModifier> add, Operation<Void> original, @Local AttributeInstance instance) {
        var call = JeiPluginCallContext.currentCall().orElse(null);
        if (call == null || !MineColoniesAttributeModifiers.shouldRepair(
            JeiOptFeatureFlags.fixMineColoniesAttributeModifiers(), call.phase(), call.pluginClass())) {
            original.call(modifiers, add);
            return;
        }
        original.call(modifiers, MineColoniesAttributeModifiers.equipmentConsumer(instance::removeModifier, add));
    }
}