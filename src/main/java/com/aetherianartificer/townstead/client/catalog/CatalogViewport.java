package com.aetherianartificer.townstead.client.catalog;

/** One coordinate transform for drawing, picking, panning, and cursor-anchored zoom. */
public final class CatalogViewport {
    public double panX, panY, zoom = 1;
    public double worldX(double screenX) { return screenX / zoom - panX; }
    public double worldY(double screenY) { return screenY / zoom - panY; }
    public double screenX(double worldX) { return (worldX + panX) * zoom; }
    public double screenY(double worldY) { return (worldY + panY) * zoom; }
    public void pan(double dx, double dy) { panX += dx / zoom; panY += dy / zoom; }
    public void zoomAt(double amount, double x, double y) {
        double wx = worldX(x), wy = worldY(y);
        zoom = Math.max(0.25, Math.min(2, zoom * Math.pow(1.12, amount)));
        panX = x / zoom - wx; panY = y / zoom - wy;
    }
    public void fit(int contentW, int contentH, int width, int height) {
        zoom = Math.max(0.25, Math.min(1, Math.min(width / (double) Math.max(1, contentW),
                height / (double) Math.max(1, contentH))));
        panX = (width / zoom - contentW) / 2; panY = (height / zoom - contentH) / 2;
    }
    public void reveal(int x, int y, int width, int height) {
        if (screenX(x) < 12 || screenX(x + 26) > width - 12) panX = width / (2 * zoom) - x - 13;
        if (screenY(y) < 12 || screenY(y + 48) > height - 12) panY = height / (2 * zoom) - y - 24;
    }
}
