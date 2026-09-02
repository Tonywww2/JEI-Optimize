package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.config.JeiOptFeatureFlags;
import com.tonywww.jeioptimize.integration.SophisticatedStorageShulkerRecipeAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.Optional;

@Pseudo
@Mixin(targets = "mezz.jei.library.recipes.ExtendableRecipeCategoryHelper", remap = false)
public abstract class ExtendableRecipeCategoryHelperMixin {
    @Shadow(remap = false)
    @Final
    private Map<Object, Object> cache;

    @Shadow(remap = false)
    public abstract Optional<?> getOptionalRecipeExtension(Object recipe);

    @Inject(
        method = "getOptionalRecipeExtension(Ljava/lang/Object;)Ljava/util/Optional;",
        at = @At("RETURN"),
        cancellable = true,
        require = 1
    )
    private void jeiOptimize$useComposedShulkerRecipeExtension(
        Object recipe,
        CallbackInfoReturnable<Optional<?>> callbackInfo
    ) {
        Optional<?> original = callbackInfo.getReturnValue();
        if (!JeiOptFeatureFlags.enabled()
            || original == null
            || original.isPresent()
            || !(recipe instanceof SophisticatedStorageShulkerRecipeAccess wrapper)) {
            return;
        }

        try {
            Object compose = wrapper.jeiOptimize$getCompose();
            if (compose == null || compose == recipe) {
                return;
            }

            Optional<?> extension = getOptionalRecipeExtension(compose);
            if (extension.isPresent()) {
                this.cache.put(recipe, extension.get());
                callbackInfo.setReturnValue(extension);
            }
        } catch (RuntimeException | LinkageError ignored) {
            callbackInfo.setReturnValue(original);
        }
    }
}