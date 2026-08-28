package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.recipe.ItemStackRepresentativeSelector;
import com.tonywww.jeioptimize.recipe.RepresentativeItemLimiter;
import net.minecraft.world.item.ItemStack;

public final class SfmFallingAnvilRepresentativeLimiter {
    private static final ThreadLocal<Context> CONTEXT = new ThreadLocal<>();

    private SfmFallingAnvilRepresentativeLimiter() {
    }

    public static void begin(boolean enabled, int limitPerEnchantment) {
        CONTEXT.remove();
        if (enabled) {
            CONTEXT.set(new Context(new RepresentativeItemLimiter(limitPerEnchantment)));
        }
    }

    public static boolean shouldKeep(Object enchantment, ItemStack stack, boolean canEnchant) {
        Context context = CONTEXT.get();
        if (!canEnchant || context == null) {
            return canEnchant;
        }
        return context.limiter().shouldKeep(enchantment, ItemStackRepresentativeSelector.itemKey(stack));
    }

    public static void end() {
        Context context = CONTEXT.get();
        CONTEXT.remove();
        if (context != null) {
            RepresentativeItemLimiter limiter = context.limiter();
            JeiOptimize.LOGGER.debug(
                "JEI Optimize kept {} of {} SFM Falling Anvil tool representatives across {} enchantments",
                limiter.selectedCount(),
                limiter.consideredCount(),
                limiter.groupCount()
            );
        }
    }

    private record Context(RepresentativeItemLimiter limiter) {
    }
}