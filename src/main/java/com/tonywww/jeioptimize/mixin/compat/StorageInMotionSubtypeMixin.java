package com.tonywww.jeioptimize.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Pseudo
@Mixin(
    targets = "net.p3pp3rf1y.sophisticatedstorageinmotion.compat.recipeviewers.jei.StorageInMotionJeiPlugin",
    remap = false
)
public abstract class StorageInMotionSubtypeMixin {
    @Redirect(
        method = "registerItemSubtypes",
        at = @At(
            value = "INVOKE",
            target = "Lnet/p3pp3rf1y/sophisticatedstorage/compat/recipeviewers/common/subtypes/"
                + "SubtypeInterpreters;getSubtypeInterpreters()Ljava/util/Map;",
            ordinal = 0
        ),
        require = 1,
        allow = 1
    )
    private Map<?, ?> jeiOptimize$skipDuplicateStorageSubtypes() {
        return Map.of();
    }
}