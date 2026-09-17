package com.tonywww.jeioptimize.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tonywww.jeioptimize.index.DeferredNativeSearchStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearch", remap = false)
public abstract class JeiNativeSearchBuilderMixin {
    @Coerce
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
        target = "Lmezz/jei/api/search/ISearchStorageBuilder;build()Lmezz/jei/api/search/ISearchStorage;"), require = 1)
    private Object jeiopt$retainBulkBuilder(@Coerce Object builder, Operation<Object> original) {
        Object deferred = DeferredNativeSearchStorage.defer(builder);
        return deferred != null ? deferred : original.call(builder);
    }
}