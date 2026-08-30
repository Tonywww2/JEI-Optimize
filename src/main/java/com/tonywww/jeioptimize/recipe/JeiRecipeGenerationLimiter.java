package com.tonywww.jeioptimize.recipe;

import com.tonywww.jeioptimize.JeiOptimize;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.runtime.IIngredientManager;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

public final class JeiRecipeGenerationLimiter {
    private static final ThreadLocal<Deque<Context>> ANVIL = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Context>> GRINDSTONE = ThreadLocal.withInitial(ArrayDeque::new);

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

    public static void clearAnvil() {
        ANVIL.remove();
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

    public static void clearGrindstone() {
        GRINDSTONE.remove();
    }

    private static void begin(
        ThreadLocal<Deque<Context>> contextHolder,
        IIngredientManager ingredientManager,
        int limitPerEnchantment
    ) {
        try {
            IIngredientHelper<ItemStack> ingredientHelper = ingredientManager.getIngredientHelper(VanillaTypes.ITEM_STACK);
            contextHolder.get().push(new Context(ingredientHelper, new RepresentativeItemLimiter(limitPerEnchantment)));
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean shouldKeep(
        ThreadLocal<Deque<Context>> contextHolder,
        Object enchantmentGroup,
        ItemStack stack
    ) {
        Context context = contextHolder.get().peek();
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

    private static void end(ThreadLocal<Deque<Context>> contextHolder, String category) {
        Deque<Context> contexts = contextHolder.get();
        Context context = contexts.poll();
        if (contexts.isEmpty()) {
            contextHolder.remove();
        }
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