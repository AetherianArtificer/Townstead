package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.GeneDisplay;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneCatalogEntryTest {

    @Test
    void singleEyeVisibilitySurvivesTheCatalogAndOlderEyesRemainTwoSided() {
        String packed = GeneDisplay.eyes("test:eyes", false, 8, "", "right").targetId();
        GeneCatalogEntry entry = new GeneCatalogEntry(
                "test:eyes", "Eyes", "", "appearance", GeneDisplay.Kind.VARIANTS.ordinal(),
                0f, 1f, packed, 0f, 0, "", 1, List.of(),
                "", "", "eyes", "", "", List.of(), List.of(), "");
        assertEquals("right", entry.eyesVisibleHalf());
        assertEquals(8, entry.eyesRow());
        assertEquals("test:eyes;0;8;", GeneDisplay.eyes("test:eyes", false, 8, "").targetId());
    }

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
        assertEquals(0, entry.skinOverlayTintBlend());
        assertEquals(1f, entry.skinOverlayTintStrength());
    }

    @Test
    void skinOverlayReadsPackedBlendAndStrength() {
        GeneCatalogEntry entry = new GeneCatalogEntry(
                "test:marks", "Marks", "", "appearance",
                GeneDisplay.Kind.SKIN_OVERLAY.ordinal(), 0f, 1f,
                "test:textures/overlay/basic.png;skin;12;3;0.65", 0f,
                0, "", 1, List.of(), "", "", "", "", "", List.of(), List.of(), "");

        assertEquals("skin", entry.skinOverlayTint());
        assertEquals(12, entry.skinOverlayOrder());
        assertEquals(3, entry.skinOverlayTintBlend());
        assertEquals(0.65f, entry.skinOverlayTintStrength());
    }
}
