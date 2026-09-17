package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptUiRefreshBatch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation", remap = false)
public abstract class JeiStartupGridRefreshMixin {
    @Invoker("lambda$new$0")
    protected abstract void jeiopt$refreshGrid();

    @Inject(method = "lambda$new$0()V", at = @At("HEAD"), cancellable = true, require = 1)
    private void jeiopt$deferStartupLayout(CallbackInfo callback) {
        if (JeiOptUiRefreshBatch.defer(this, this::jeiopt$refreshGrid)) {
            callback.cancel();
        }
    }
}