package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.SophisticatedStorageShulkerRecipeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.ExtendableRecipeCategoryHelper", remap = false)
public abstract class ExtendableRecipeCategoryHelperMixin {
    @Shadow(remap = false)
    public abstract Optional<?> getOptionalRecipeExtension(Object recipe);

    @Inject(
        method = "getOptionalRecipeExtension(Ljava/lang/Object;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true
    )
    private void jeiOptimize$useComposedShulkerRecipeExtension(
        Object recipe,
        CallbackInfoReturnable<Optional<?>> callbackInfo
    ) {
        if (!JeiOptFeatureFlags.enabled()
            || callbackInfo.getReturnValue().isPresent()
            || !(recipe instanceof SophisticatedStorageShulkerRecipeAccess wrapper)) {
            return;
        }

        Object compose = wrapper.jeiOptimize$getCompose();
        if (compose == null || compose == recipe) {
            return;
        }

        Optional<?> extension = getOptionalRecipeExtension(compose);
        if (extension.isPresent()) {
            callbackInfo.setReturnValue(extension);
        }
    }
}