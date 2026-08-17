package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipesGui", remap = false)
public abstract class JeiRecipesGuiGuardMixin {
    @Inject(
        method = {
            "show(Ljava/util/List;)V",
            "showTypes(Ljava/util/List;)V",
            "showRecipes(Lmezz/jei/api/recipe/category/IRecipeCategory;Ljava/util/List;Ljava/util/List;)V"
        },
        at = @At("HEAD"),
        cancellable = true
    )
    private void jeiOptimize$ignoreShowUntilRuntimeReady(CallbackInfo callbackInfo) {
        if (JeiOptStartupProgressState.blocksJeiInput()) {
            callbackInfo.cancel();
        }
    }
}