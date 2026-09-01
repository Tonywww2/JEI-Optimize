package com.tonywww.jeioptimize.integration;

import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public final class SfmFallingAnvilCache {
    private static final ThreadLocal<Capture> CAPTURE = new ThreadLocal<>();
    private static volatile CachedLayout cachedLayout;

    private SfmFallingAnvilCache() {
    }

    public static void clear() {
        CAPTURE.remove();
        cachedLayout = null;
    }

    public static void beginCapture(boolean enabled) {
        CAPTURE.remove();
        if (enabled && !hasCurrentLayout()) {
            CAPTURE.set(new Capture(new ArrayList<>(4)));
        }
    }

    public static void capture(List<ItemStack> stacks) {
        Capture capture = CAPTURE.get();
        if (capture != null) {
            capture.lists().add(List.copyOf(stacks));
        }
    }

    public static void endCapture() {
        Capture capture = CAPTURE.get();
        CAPTURE.remove();
        if (capture == null || capture.lists().size() != 4) {
            return;
        }
        cachedLayout = new CachedLayout(
            JeiOptRuntimeState.currentGeneration(),
            capture.lists().get(0),
            capture.lists().get(1),
            capture.lists().get(2),
            capture.lists().get(3)
        );
        JeiOptimize.LOGGER.debug(
            "JEI Optimize cached SFM Falling Anvil display lists: {} enchanted inputs and {} book outputs",
            capture.lists().get(2).size(),
            capture.lists().get(3).size()
        );
    }

    public static boolean addCachedLayout(IRecipeLayoutBuilder builder) {
        CachedLayout layout = cachedLayout;
        if (layout == null || layout.generation() != JeiOptRuntimeState.currentGeneration()) {
            return false;
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 8, 0).addItemStacks(layout.anvils());
        builder.addSlot(RecipeIngredientRole.CATALYST, 8, 36).addItemStacks(layout.crushingBlocks());
        builder.addSlot(RecipeIngredientRole.INPUT, 18, 18).addItemStack(new ItemStack(Items.BOOK));
        IRecipeSlotBuilder input = builder.addSlot(RecipeIngredientRole.INPUT, 0, 18)
            .addItemStacks(layout.enchantedInputs());
        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, 50, 18)
            .addItemStacks(layout.enchantedBooks());
        builder.createFocusLink(input, output);
        return true;
    }

    private static boolean hasCurrentLayout() {
        CachedLayout layout = cachedLayout;
        return layout != null && layout.generation() == JeiOptRuntimeState.currentGeneration();
    }

    private record Capture(List<List<ItemStack>> lists) {
    }

    private record CachedLayout(
        long generation,
        List<ItemStack> anvils,
        List<ItemStack> crushingBlocks,
        List<ItemStack> enchantedInputs,
        List<ItemStack> enchantedBooks
    ) {
    }
}