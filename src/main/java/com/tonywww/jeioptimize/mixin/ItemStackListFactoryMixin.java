package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.content.CreativeTabFilter;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Pseudo
@Mixin(targets = "mezz.jei.library.plugins.vanilla.ingredients.ItemStackListFactory", remap = false)
public abstract class ItemStackListFactoryMixin {
    // The target is a Minecraft method, so it still needs remapping inside this remap=false mixin.
    @Redirect(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/CreativeModeTabs;allTabs()Ljava/util/List;",
            remap = true
        )
    )
    private static List<CreativeModeTab> jeiopt$skipConfiguredCreativeTabs() {
        return CreativeTabFilter.apply(CreativeModeTabs.allTabs());
    }
}
