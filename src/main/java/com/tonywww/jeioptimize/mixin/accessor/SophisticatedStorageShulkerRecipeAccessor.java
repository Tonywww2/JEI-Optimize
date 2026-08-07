package com.tonywww.jeioptimize.mixin.accessor;

import com.tonywww.jeioptimize.integration.SophisticatedStorageShulkerRecipeAccess;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

@Pseudo
@Mixin(
    targets = "net.p3pp3rf1y.sophisticatedstorage.crafting.ShulkerBoxFromVanillaShapelessRecipe",
    remap = false
)
public abstract class SophisticatedStorageShulkerRecipeAccessor implements SophisticatedStorageShulkerRecipeAccess {
    @Accessor(value = "compose", remap = false)
    @Override
    public abstract ShapelessRecipe jeiOptimize$getCompose();
}