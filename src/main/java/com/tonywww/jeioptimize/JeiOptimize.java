package com.tonywww.jeioptimize;

import com.mojang.logging.LogUtils;
import com.tonywww.jeioptimize.config.JeiOptConfig;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

@Mod(JeiOptimize.MOD_ID)
public final class JeiOptimize {
    public static final String MOD_ID = "jei_optimize";
    public static final Logger LOGGER = LogUtils.getLogger();

    public JeiOptimize() {
        JeiOptConfig.register();
    }
}