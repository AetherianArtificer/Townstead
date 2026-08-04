package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneCatalogEntryTest {

    @Test
    void variantSkinOverlayRetainsItsRenderKindWithoutBecomingAFaceGene() {
        GeneCatalogEntry entry = new GeneCatalogEntry(
                "test:marks", "Marks", "", "appearance",
                GeneDisplay.Kind.VARIANTS.ordinal(), 0f, 1f,
                "test:textures/overlay/basic.png;", 0f,
                0, "", 1,
                List.of(new GeneCatalogEntry.Variant(
                        "basic", "Basic", 1, "", -1,
                        "test:textures/overlay/basic.png", false, "", List.of(), List.of())),
                "", "", "skin_overlay", "", "", List.of(), List.of(), "");

        assertTrue(entry.isVariants());
        assertTrue(entry.isSkinOverlay());
        assertFalse(entry.isFace());
        assertEquals(0, entry.skinOverlayOrder());
    }

    @Test
    void skinOverlayReadsPackedOrder() {
        GeneCatalogEntry entry = new GeneCatalogEntry(
                "test:marks", "Marks", "", "appearance",
                GeneDisplay.Kind.SKIN_OVERLAY.ordinal(), 0f, 1f,
                "test:textures/overlay/basic.png;skin;12", 0f,
                0, "", 1, List.of(), "", "", "", "", "", List.of(), List.of(), "");

        assertEquals("skin", entry.skinOverlayTint());
        assertEquals(12, entry.skinOverlayOrder());
    }
}
