package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

public final class JeiRecipeGenerationLimiter {
    private static final ThreadLocal<Context> ANVIL = new ThreadLocal<>();
    private static final ThreadLocal<Context> GRINDSTONE = new ThreadLocal<>();

    private JeiRecipeGenerationLimiter() {
    }

    public static void beginAnvil(IIngredientManager ingredientManager, int limitPerEnchantment) {
        begin(ANVIL, ingredientManager, limitPerEnchantment);
    }

    public static boolean shouldKeepAnvil(Object enchantmentGroup, ItemStack stack) {
        return shouldKeep(ANVIL, enchantmentGroup, stack);
    }

    public static void endAnvil() {
        end(ANVIL, "anvil");
    }

    public static void beginGrindstone(IIngredientManager ingredientManager, int limitPerEnchantment) {
        begin(GRINDSTONE, ingredientManager, limitPerEnchantment);
    }

    public static boolean shouldKeepGrindstone(Object enchantmentGroup, ItemStack stack) {
        return shouldKeep(GRINDSTONE, enchantmentGroup, stack);
    }

    public static void endGrindstone() {
        end(GRINDSTONE, "grindstone");
    }

    private static void begin(
        ThreadLocal<Context> contextHolder,
        IIngredientManager ingredientManager,
        int limitPerEnchantment
    ) {
        contextHolder.remove();
        try {
            IIngredientHelper<ItemStack> ingredientHelper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            contextHolder.set(new Context(ingredientHelper, new RepresentativeItemLimiter(limitPerEnchantment)));
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean shouldKeep(
        ThreadLocal<Context> contextHolder,
        Object enchantmentGroup,
        ItemStack stack
    ) {
        Context context = contextHolder.get();
        if (context == null || enchantmentGroup == null || stack == null) {
            return true;
        }

        String itemKey;
        try {
            itemKey = context.ingredientHelper().getWildcardId(stack);
        } catch (RuntimeException ignored) {
            itemKey = stack.getDescriptionId();
        }
        return context.limiter().shouldKeep(enchantmentGroup, itemKey);
    }

    private static void end(ThreadLocal<Context> contextHolder, String category) {
        Context context = contextHolder.get();
        contextHolder.remove();
        if (context != null) {
            RepresentativeItemLimiter limiter = context.limiter();
            JeiOptimize.LOGGER.debug(
                "JEI Optimize {} representative selection kept {} of {} compatible items across {} enchantments",
                category,
                limiter.selectedCount(),
                limiter.consideredCount(),
                limiter.groupCount()
            );
        }
    }

    private record Context(
        IIngredientHelper<ItemStack> ingredientHelper,
        RepresentativeItemLimiter limiter
    ) {
    }
}