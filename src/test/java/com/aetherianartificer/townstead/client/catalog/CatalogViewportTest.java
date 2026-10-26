package com.aetherianartificer.townstead.client.catalog;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogViewportTest {
    @Test void zoomKeepsWorldPointUnderCursor() {
        CatalogViewport camera = new CatalogViewport(); camera.pan(37, -28);
        double x = camera.worldX(130), y = camera.worldY(90);
        camera.zoomAt(3, 130, 90);
        assertEquals(x, camera.worldX(130), 1e-9); assertEquals(y, camera.worldY(90), 1e-9);
        assertEquals(130, camera.screenX(x), 1e-9); assertEquals(90, camera.screenY(y), 1e-9);
    }
    @Test void fitAndRevealKeepUsableBounds() {
        CatalogViewport camera = new CatalogViewport(); camera.fit(700, 500, 350, 250);
        assertEquals(0.5, camera.zoom); assertEquals(350, camera.screenX(700), 1e-9);
        camera.reveal(1000, 700, 350, 250);
        assertTrue(camera.screenX(1000) > 0); assertTrue(camera.screenX(1026) < 350);
        assertTrue(camera.screenY(700) > 0); assertTrue(camera.screenY(748) < 250);
    }
}
