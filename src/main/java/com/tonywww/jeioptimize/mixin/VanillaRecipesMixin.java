package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.recipe.VanillaRecipeParallelBuilder;
import com.tonywww.jeioptimize.runtime.JeiOptRuntimeState;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
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
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.crafting.VanillaRecipes", remap = false)
public abstract class VanillaRecipesMixin {
    @Shadow
    @Final
    private RecipeManager recipeManager;

    @Shadow
    @Final
    private IIngredientManager ingredientManager;

    @Inject(method = "getCraftingRecipes", at = @At("HEAD"), cancellable = true)
    //? if forge {
    private void jeiopt$parallelCraftingRecipes(
        IRecipeCategory<CraftingRecipe> craftingCategory,
        CallbackInfoReturnable<Map<Boolean, List<CraftingRecipe>>> callbackInfo
    ) {
    //?} else {
    /*private void jeiopt$parallelCraftingRecipes(
        IRecipeCategory<RecipeHolder<CraftingRecipe>> craftingCategory,
        CallbackInfoReturnable<Map<Boolean, List<RecipeHolder<CraftingRecipe>>>> callbackInfo
    ) {
    *///?}
        if (!JeiOptFeatureFlags.parallelVanillaRecipes()) {
            return;
        }
        callbackInfo.setReturnValue(VanillaRecipeParallelBuilder.buildCraftingRecipes(
            this.recipeManager, this.ingredientManager, craftingCategory));
    }

    @Redirect(
        method = "getValidHandledRecipes",
        at = @At(value = "INVOKE", target = "Ljava/util/List;stream()Ljava/util/stream/Stream;")
    )
    private static Stream<?> jeiopt$parallelValidationStream(List<?> recipes) {
        // Parallel only on the initial exclusive startup; after any runtime unload (rebuild/reload)
        // fall back to sequential, so the main thread never joins a pool task that may itself need
        // the main thread during an in-world recipe rebuild (deadlock). See VanillaRecipeParallelBuilder.
        return JeiOptFeatureFlags.parallelVanillaRecipes() && !JeiOptRuntimeState.hasRuntimeUnloadedOnce()
            ? recipes.parallelStream()
            : recipes.stream();
    }
}
