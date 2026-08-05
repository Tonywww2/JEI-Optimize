package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.recipe.VanillaRecipeWarmup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
//? if neoforge {
/*import net.minecraft.world.item.crafting.RecipeHolder;
*///?}
import net.minecraft.world.item.crafting.RecipeManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.crafting.VanillaRecipes", remap = false)
public abstract class VanillaRecipesMixin {
    @Shadow
    @Final
    private RecipeManager recipeManager;

    // getCraftingRecipes is the first thing VanillaPlugin.registerRecipes calls, so this warms the
    // ingredients for every vanilla category before JEI validates any of them.
    @Inject(method = "getCraftingRecipes", at = @At("HEAD"))
    //? if forge {
    private void jeiopt$warmRecipeIngredients(
        IRecipeCategory<CraftingRecipe> craftingCategory,
        CallbackInfoReturnable<Map<Boolean, List<CraftingRecipe>>> callbackInfo
    ) {
    //?} else {
    /*private void jeiopt$warmRecipeIngredients(
        IRecipeCategory<RecipeHolder<CraftingRecipe>> craftingCategory,
        CallbackInfoReturnable<Map<Boolean, List<RecipeHolder<CraftingRecipe>>>> callbackInfo
    ) {
    *///?}
        VanillaRecipeWarmup.warmUp(this.recipeManager);
    }
}
