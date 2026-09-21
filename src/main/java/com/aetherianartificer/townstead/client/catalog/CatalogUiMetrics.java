package com.aetherianartificer.townstead.client.catalog;

/** Keep a usable catalog workspace at high Minecraft GUI scales, with whole physical pixels. */
public record CatalogUiMetrics(float scale, int width, int height) {
    public static CatalogUiMetrics forScreen(int width, int height, double guiScale) {
        double pixels = Math.max(1, Math.floor(Math.min(width * guiScale / 800, height * guiScale / 420)));
        float scale = (float) Math.min(1, pixels / guiScale);
        return new CatalogUiMetrics(scale, (int) (width / scale), (int) (height / scale));
    }
}
