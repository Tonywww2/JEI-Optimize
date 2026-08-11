package com.tonywww.jeioptimize.mixin;

import com.tonywww.jeioptimize.runtime.JeiOptStartupProgressState;
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
    private static final int MAX_PANEL_WIDTH = 160;
    private static final int MIN_PANEL_WIDTH = 84;
    private static final int HORIZONTAL_MARGIN = 6;
    private static final int PADDING = 6;
    private static final int BAR_HEIGHT = 6;

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
        JeiOptStartupProgressState.Snapshot progress = JeiOptStartupProgressState.snapshot();
        if (!progress.visible()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int panelLeft = leftPos + imageWidth;
        int availableWidth = minecraft.getWindow().getGuiScaledWidth() - panelLeft;
        int panelWidth = Math.min(MAX_PANEL_WIDTH, availableWidth - HORIZONTAL_MARGIN * 2);
        if (panelWidth < MIN_PANEL_WIDTH) {
            return;
        }

        Component status = jeiOptimize$statusText(progress);
        int contentWidth = panelWidth - PADDING * 2;
        String statusText = font.plainSubstrByWidth(status.getString(), contentWidth);
        int statusWidth = font.width(statusText);
        int panelX = minecraft.getWindow().getGuiScaledWidth() - HORIZONTAL_MARGIN - panelWidth;
        int panelY = Math.max(HORIZONTAL_MARGIN, topPos + HORIZONTAL_MARGIN);
        int panelHeight = PADDING + font.lineHeight + 5 + BAR_HEIGHT + PADDING;
        int barX = panelX + PADDING;
        int barY = panelY + PADDING + font.lineHeight + 5;

        guiGraphics.fill(
            panelX,
            panelY,
            panelX + panelWidth,
            panelY + panelHeight,
            0xB0101010
        );
        guiGraphics.drawString(
            font,
            statusText,
            panelX + (panelWidth - statusWidth) / 2,
            panelY + PADDING,
            0xFFFFFFFF,
            true
        );
        guiGraphics.fill(barX, barY, barX + contentWidth, barY + BAR_HEIGHT, 0xFF303030);
        guiGraphics.fill(barX, barY, barX + contentWidth, barY + 1, 0xFF606060);
        jeiOptimize$drawProgress(guiGraphics, progress, barX, barY, contentWidth);
    }

    private static Component jeiOptimize$statusText(JeiOptStartupProgressState.Snapshot progress) {
        return switch (progress.stage()) {
            case INDEXING -> Component.translatable(
                "gui.justenoughthreads.loading.indexing",
                progress.completedChunks(),
                progress.totalChunks()
            );
            case READY, PUBLISHED -> Component.translatable("gui.justenoughthreads.loading.publishing");
            default -> Component.translatable("gui.justenoughthreads.loading.preparing");
        };
    }

    private static void jeiOptimize$drawProgress(
        GuiGraphics guiGraphics,
        JeiOptStartupProgressState.Snapshot progress,
        int barX,
        int barY,
        int barWidth
    ) {
        if (progress.stage() == JeiOptStartupProgressState.Stage.PREPARING) {
            int segmentWidth = Math.max(12, barWidth / 3);
            int travel = Math.max(1, barWidth - segmentWidth);
            int offset = (int) ((System.currentTimeMillis() / 12L) % (travel * 2L));
            if (offset > travel) {
                offset = travel * 2 - offset;
            }
            guiGraphics.fill(
                barX + offset,
                barY + 1,
                barX + offset + segmentWidth,
                barY + BAR_HEIGHT,
                0xFFE6A23C
            );
            return;
        }

        int filledWidth = (int) Math.round(barWidth * progress.fraction());
        if (filledWidth > 0) {
            guiGraphics.fill(barX, barY + 1, barX + filledWidth, barY + BAR_HEIGHT, 0xFFE6A23C);
        }
    }
}