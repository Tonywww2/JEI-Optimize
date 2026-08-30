package com.tonywww.jeioptimize.mixin;

//? if forge {
import com.tonywww.jeioptimize.JeiOptimize;
import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.LegacyLazyRecipeLayoutList;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.common.config.RecipeSorterStage;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtonsFactory;
import mezz.jei.gui.recipes.layouts.IRecipeLayoutList;
import mezz.jei.gui.recipes.lookups.IFocusedRecipes;
import mezz.jei.gui.recipes.lookups.ILookupState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Set;

@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public abstract class RecipeGuiLogicLegacyMixin {
    @Shadow(remap = false)
    @Final
    private IRecipeManager recipeManager;

    @Shadow(remap = false)
    @Final
    private IRecipeLayoutWithButtonsFactory recipeLayoutFactory;

    @Shadow(remap = false)
    private ILookupState state;

    @Inject(
        method = "createRecipeLayoutsWithButtons(Ljava/util/Set;Lmezz/jei/gui/recipes/lookups/IFocusedRecipes;"
            + "Lnet/minecraft/world/inventory/AbstractContainerMenu;Lnet/minecraft/world/entity/player/Player;)"
            + "Lmezz/jei/gui/recipes/layouts/IRecipeLayoutList;",
        at = @At("HEAD"),
        cancellable = true
    )
    private <T> void jeiOptimize$createVisibleLayoutsOnly(
        Set<RecipeSorterStage> sorterStages,
        IFocusedRecipes<T> selectedRecipes,
        AbstractContainerMenu container,
        Player player,
        CallbackInfoReturnable<IRecipeLayoutList> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.lazyRecipeLayouts()) {
            return;
        }
        List<T> recipes = selectedRecipes.getRecipes();
        if (recipes.size() <= JeiOptFeatureFlags.lazyRecipeLayoutThreshold()) {
            return;
        }
        JeiOptimize.LOGGER.debug(
            "JEI Optimize uses lazy layouts for {} recipes in {}",
            recipes.size(),
            selectedRecipes.getRecipeCategory().getRecipeType().getUid()
        );
        callbackInfo.setReturnValue(new LegacyLazyRecipeLayoutList<>(
            recipeManager,
            recipeLayoutFactory,
            selectedRecipes.getRecipeCategory(),
            recipes,
            state.getFocuses(),
            container,
            player
        ));
    }
}
//?} else {
/*import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.recipes.RecipeGuiLogic", remap = false)
public abstract class RecipeGuiLogicLegacyMixin {
}
*///?}