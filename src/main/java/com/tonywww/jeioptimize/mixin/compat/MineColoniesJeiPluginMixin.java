package com.tonywww.jeioptimize.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.integration.MineColoniesJeiToolScanCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Pseudo
@Mixin(targets = "com.minecolonies.core.compatibility.jei.JEIPlugin", remap = false)
public abstract class MineColoniesJeiPluginMixin {
    @WrapOperation(
        method = "registerRecipes(Lmezz/jei/api/registration/IRecipeRegistration;)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/minecolonies/core/compatibility/jei/ToolRecipeCategory;findRecipes()Ljava/util/List;"
        ),
        require = 1
    )
    private List<?> jeiOptimize$cacheToolRecipeScan(Operation<List<?>> original) {
        return MineColoniesJeiToolScanCache.runScoped(original::call);
    }
}