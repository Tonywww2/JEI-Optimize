package com.tonywww.jeioptimize;

import com.mojang.logging.LogUtils;
import com.tonywww.jeioptimize.config.JeiOptConfig;
//? if forge {
import net.minecraftforge.fml.common.Mod;
//?} else {
/*import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
*///?}
import org.slf4j.Logger;

@Mod(JeiOptimize.MOD_ID)
public final class JeiOptimize {
    public static final String MOD_ID = "jei_optimize";
    public static final Logger LOGGER = LogUtils.getLogger();

    //? if forge {
    public JeiOptimize() {
        JeiOptConfig.register();
    }
    //?} else {
    /*public JeiOptimize(ModContainer container) {
        JeiOptConfig.register(container);
    }
    *///?}
}