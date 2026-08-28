package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.ProductiveTreesStripperToolCache;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

@Pseudo
@Mixin(targets = "cy.jdkdigital.productivetrees.integrations.jei.LogStrippingRecipeCategory", remap = false)
public abstract class ProductiveTreesLogStrippingCategoryMixin {
    @WrapOperation(
        method = "setRecipe",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/Ingredient;of(Lnet/minecraft/tags/TagKey;)Lnet/minecraft/world/item/crafting/Ingredient;",
            remap = true
        )
    )
    private Ingredient jeiOptimize$cacheStripperTools(
        TagKey<Item> tag,
        Operation<Ingredient> original
    ) {
        if (!JeiOptFeatureFlags.cacheProductiveTreesStripperTools()) {
            return original.call(tag);
        }
        return ProductiveTreesStripperToolCache.get(() -> original.call(tag));
    }
}