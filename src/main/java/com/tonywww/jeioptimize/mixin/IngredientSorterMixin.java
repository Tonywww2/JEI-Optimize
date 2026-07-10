package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.runtime.JeiOptCacheScope;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "mezz.jei.gui.ingredients.IngredientSorterComparators", remap = false)
public abstract class IngredientSorterMixin {
    @org.spongepowered.asm.mixin.injection.Inject(
        method = "tagCount",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void jeiOptimize$getCachedTagCount(ResourceLocation tagId, CallbackInfoReturnable<Integer> cir) {
        if (!JeiOptFeatureFlags.sortKeyCache()) {
            return;
        }
        int tagCount = JeiOptCacheScope.getOrCompute(
            JeiOptCacheScope.TAG_COUNTS,
            tagId,
            () -> jeiOptimize$countTag(tagId)
        );
        cir.setReturnValue(tagCount);
    }

    private static int jeiOptimize$countTag(ResourceLocation tagId) {
        if (tagId.toString().equals("itemfilters:check_nbt")) {
            return 0;
        }
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        return BuiltInRegistries.ITEM.getTag(tagKey)
            .map(HolderSet.ListBacked::size)
            .orElse(0);
    }
}