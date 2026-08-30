package com.tonywww.jeioptimize.instrumentation;

//? if forge {
import net.minecraftforge.fml.ModList;
//?} else {
/*import net.neoforged.fml.ModList;
*///?}

public final class JeiRuntimeVersion {
    private JeiRuntimeVersion() {
    }

    public static String detect() {
        try {
            return ModList.get()
                .getModContainerById("jei")
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
        } catch (RuntimeException | LinkageError error) {
            return "unknown";
        }
    }
}