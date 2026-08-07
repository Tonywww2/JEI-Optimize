package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
public abstract class JeiLoadingOverlayMixin {
    private static final Component LOADING_TEXT = Component.translatable("gui.jei_optimize.loading");
    private static final int HORIZONTAL_PADDING = 6;
    private static final int VERTICAL_PADDING = 4;

    @Shadow
    protected int imageWidth;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Inject(method = "render", at = @At("TAIL"))
    private void jeiOptimize$renderLoadingMessage(
        GuiGraphics guiGraphics,
        int mouseX,
        int mouseY,
        float partialTick,
        CallbackInfo callbackInfo
    ) {
        if (!JeiOptExecutors.isJeiStartRunning()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int panelLeft = leftPos + imageWidth;
        int availableWidth = minecraft.getWindow().getGuiScaledWidth() - panelLeft;
        int textWidth = font.width(LOADING_TEXT);
        if (availableWidth < textWidth + HORIZONTAL_PADDING * 2) {
            return;
        }

        int textX = panelLeft + (availableWidth - textWidth) / 2;
        int textY = Math.max(VERTICAL_PADDING, topPos + VERTICAL_PADDING);
        guiGraphics.fill(
            textX - HORIZONTAL_PADDING,
            textY - VERTICAL_PADDING,
            textX + textWidth + HORIZONTAL_PADDING,
            textY + font.lineHeight + VERTICAL_PADDING,
            0xB0101010
        );
        guiGraphics.drawString(font, LOADING_TEXT, textX, textY, 0xFFFFFFFF, true);
    }
}