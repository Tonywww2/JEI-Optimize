package com.tonywww.jeioptimize.mixin.accessor;

import mezz.jei.api.runtime.IJeiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(targets = "mezz.jei.common.Internal", remap = false)
public interface JeiRuntimeAccessor {
    @Accessor("jeiRuntime")
    static IJeiRuntime jeiopt$getNullableRuntime() {
        throw new AssertionError("replaced by mixin");
    }
}