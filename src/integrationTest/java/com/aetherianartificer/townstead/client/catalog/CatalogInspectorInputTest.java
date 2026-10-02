package com.aetherianartificer.townstead.client.catalog;

import com.aetherianartificer.townstead.client.gui.common.CatalogInspectorWidget;
import com.aetherianartificer.townstead.client.gui.common.ScaledWidget;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CatalogInspectorInputTest {
    @Test void scaledScrollbarRespondsToClicksDragsAndRelease() throws Exception {
        var inspector = new CatalogInspectorWidget(null, 20, 30, 200, 300);
        // Supply the measured ingredient height without needing a rendering context.
        var content = CatalogInspectorWidget.class.getDeclaredField("contentHeight");
        content.setAccessible(true);
        content.setInt(inspector, 500);
        var offset = CatalogInspectorWidget.class.getDeclaredField("scroll");
        offset.setAccessible(true);
        var input = new ScaledWidget(inspector, 0.5f);

        assertFalse(input.mouseClicked(108, 130, 1));
        assertTrue(input.mouseClicked(108, 130, 0));
        int picked = offset.getInt(inspector);
        assertTrue(picked > 0 && picked < 365);
        // Dragging beyond the track remains captured and clamps at the end of the list.
        assertTrue(input.mouseDragged(108, 200, 0, 0, 70));
        assertEquals(365, offset.getInt(inspector));
        assertTrue(input.mouseDragged(108, 80, 0, 0, -120));
        assertEquals(0, offset.getInt(inspector));
        assertTrue(input.mouseReleased(108, 80, 0));
        assertFalse(input.mouseDragged(108, 200, 0, 0, 120));
        assertEquals(0, offset.getInt(inspector));
    }
}
