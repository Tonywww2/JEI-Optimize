package com.tonywww.jeioptimize.ui;

public final class LoadingPanelLayout {
    private LoadingPanelLayout() {
    }

    public static Layout calculate(
        int screenWidth,
        int screenHeight,
        int containerLeft,
        int containerTop,
        int containerWidth,
        int containerHeight,
        int preferredWidth,
        int minimumWidth,
        int panelHeight,
        int margin
    ) {
        int screenPanelWidth = Math.min(preferredWidth, screenWidth - margin * 2);
        if (screenPanelWidth < minimumWidth || panelHeight > screenHeight - margin * 2) {
            return null;
        }

        int containerRight = containerLeft + containerWidth;
        int containerBottom = containerTop + containerHeight;
        Layout best = null;

        int rightWidth = Math.min(preferredWidth, screenWidth - containerRight - margin * 2);
        if (rightWidth >= minimumWidth) {
            best = new Layout(
                containerRight + margin,
                clamp(containerTop + margin, margin, screenHeight - margin - panelHeight),
                rightWidth
            );
        }

        int leftWidth = Math.min(preferredWidth, containerLeft - margin * 2);
        if (leftWidth >= minimumWidth && (best == null || leftWidth > best.width())) {
            best = new Layout(
                containerLeft - margin - leftWidth,
                clamp(containerTop + margin, margin, screenHeight - margin - panelHeight),
                leftWidth
            );
        }

        if (screenHeight - containerBottom - margin * 2 >= panelHeight
            && (best == null || screenPanelWidth > best.width())) {
            best = new Layout(
                clamp(containerLeft + (containerWidth - screenPanelWidth) / 2, margin, screenWidth - margin - screenPanelWidth),
                containerBottom + margin,
                screenPanelWidth
            );
        }

        if (containerTop - margin * 2 >= panelHeight
            && (best == null || screenPanelWidth > best.width())) {
            best = new Layout(
                clamp(containerLeft + (containerWidth - screenPanelWidth) / 2, margin, screenWidth - margin - screenPanelWidth),
                containerTop - margin - panelHeight,
                screenPanelWidth
            );
        }

        if (best != null) {
            return best;
        }

        return new Layout(
            screenWidth - margin - screenPanelWidth,
            screenHeight - margin - panelHeight,
            screenPanelWidth
        );
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    public record Layout(int x, int y, int width) {
    }
}