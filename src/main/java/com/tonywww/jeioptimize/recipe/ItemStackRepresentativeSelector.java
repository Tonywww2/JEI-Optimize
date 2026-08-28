package com.tonywww.jeioptimize.recipe;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class ItemStackRepresentativeSelector {
    private ItemStackRepresentativeSelector() {
    }

    public static List<ItemStack> selectFamilies(List<ItemStack> stacks, int limit) {
        return ParallelRepresentativeSelector.selectFamilies(stacks, ItemStackRepresentativeSelector::itemKey, limit);
    }

    public static ParallelSelection selectParallelFamilies(
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        int limit
    ) {
        ParallelRepresentativeSelector.Selection<ItemStack, ItemStack> selection =
            ParallelRepresentativeSelector.selectParallelFamilies(
                inputs,
                outputs,
                ItemStackRepresentativeSelector::itemKey,
                limit
            );
        return new ParallelSelection(selection.inputs(), selection.outputs());
    }

    public static ParallelSelection selectParallelExamples(
        List<ItemStack> inputs,
        List<ItemStack> outputs,
        int limit
    ) {
        ParallelRepresentativeSelector.Selection<ItemStack, ItemStack> selection =
            ParallelRepresentativeSelector.selectParallelExamples(inputs, outputs, limit);
        return new ParallelSelection(selection.inputs(), selection.outputs());
    }

    public static String itemKey(ItemStack stack) {
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? stack.getDescriptionId() : key.toString();
    }

    public record ParallelSelection(List<ItemStack> inputs, List<ItemStack> outputs) {
    }
}