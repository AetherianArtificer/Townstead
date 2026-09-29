package com.aetherianartificer.townstead.clothing.adapt;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ClothingAdapterTest {
    private static final int CLOTH = 0xFF203040;
    private static final int SKIN = 0xFFE0B090;

    @Test
    void oldSkinsGainAMirroredLeftLeg() {
        BufferedImage skin = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        skin.setRGB(0, 20, CLOTH);
        BufferedImage out = ClothingAdapter.adapt(skin, false, 0, 3);
        assertEquals(CLOTH, out.getRGB(24 + 3, 52));
    }

    @Test
    void faceAndHairAreClearedButAHatStays() {
        BufferedImage skin = filled(CLOTH);
        BufferedImage out = ClothingAdapter.adapt(skin, false, 3, 3);
        assertEquals(0, out.getRGB(12, 14), "face");
        assertEquals(CLOTH, out.getRGB(12, 9), "hat row");
        assertEquals(CLOTH, out.getRGB(12, 2), "hat top");
        assertEquals(0, out.getRGB(20, 2), "under the chin");
        assertEquals(0, ClothingAdapter.adapt(skin, false, 0, 3).getRGB(44, 10), "no hat clears the head overlay");
    }

    @Test
    void bareHandsAreStrippedButSleevesStay() {
        BufferedImage skin = filled(CLOTH);
        skin.setRGB(44, 31, SKIN);
        skin.setRGB(44, 24, SKIN);
        BufferedImage out = ClothingAdapter.adapt(skin, false, 0, 3);
        assertEquals(0, out.getRGB(44, 31));
        assertEquals(SKIN, out.getRGB(44, 24));
    }

    @Test
    void slimArmsFillFourPixels() {
        BufferedImage skin = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 20; y < 32; y++) {
            for (int x = 40; x < 54; x++) skin.setRGB(x, y, CLOTH);
        }
        BufferedImage out = ClothingAdapter.adapt(skin, true, 0, 0);
        assertEquals(CLOTH, out.getRGB(55, 25), "back face widened to 4px");
        assertNotEquals(0, out.getRGB(47, 25));
    }

    private static BufferedImage filled(int argb) {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) image.setRGB(x, y, argb);
        }
        return image;
    }
}
