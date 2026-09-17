package com.tonywww.jeioptimize.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Pseudo
@Mixin(targets = "mezz.jei.gui.search.ElementSearch", remap = false)
public interface ElementSearchTooltipAccessor {
    @Accessor("prefixedSearchables")
    Map<Object, Object> jeiopt$tooltipSearchables();
}