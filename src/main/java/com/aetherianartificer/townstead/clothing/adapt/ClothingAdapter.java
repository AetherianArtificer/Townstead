package com.aetherianartificer.townstead.clothing.adapt;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Turns a full player-format skin into a clothing-only texture for MCA villagers: the face, the
 * hair and bare skin come out, so the villager's own face and skin show through. Old 64x32 skins
 * gain a left arm and leg (mirrored from the right, as vanilla does) and slim 3px arms are widened
 * to MCA's 4px arms.
 */
public final class ClothingAdapter {
    private static final int SIZE = 64;

    private ClothingAdapter() {}

    /**
     * @param hatRows rows of the head's sides kept for a hat, with its top; 0 clears the whole head
     * @param handRows bottom rows of each arm where bare skin is removed
     */
    public static BufferedImage adapt(BufferedImage source, boolean slim, int hatRows, int handRows) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < Math.min(SIZE, source.getHeight()); y++) {
            for (int x = 0; x < Math.min(SIZE, source.getWidth()); x++) image.setRGB(x, y, source.getRGB(x, y));
        }
        if (legacy(image)) mirrorLegacyLimbs(image);
        if (slim) image = widenSlimArms(image);
        clearHead(image, hatRows);
        for (int[] arm : new int[][]{{40, 16}, {40, 32}, {32, 48}, {48, 48}}) stripSkin(image, arm[0], arm[1] + 16 - handRows, 16, handRows);
        stripSkin(image, 20, 20, 8, 2);
        stripSkin(image, 20, 36, 8, 2);
        return image;
    }

    private static boolean legacy(BufferedImage image) {
        for (int y = 48; y < 64; y++) {
            for (int x = 16; x < 48; x++) if (alpha(image.getRGB(x, y)) != 0) return false;
        }
        return true;
    }

    /** Vanilla's rule for old skins: the left leg and arm are the right ones, mirrored. */
    private static void mirrorLegacyLimbs(BufferedImage image) {
        copy(image, 4, 16, 20, 48, 4, 4);
        copy(image, 8, 16, 24, 48, 4, 4);
        copy(image, 0, 20, 24, 52, 4, 12);
        copy(image, 4, 20, 20, 52, 4, 12);
        copy(image, 8, 20, 16, 52, 4, 12);
        copy(image, 12, 20, 28, 52, 4, 12);
        copy(image, 44, 16, 36, 48, 4, 4);
        copy(image, 48, 16, 40, 48, 4, 4);
        copy(image, 40, 20, 40, 52, 4, 12);
        copy(image, 44, 20, 36, 52, 4, 12);
        copy(image, 48, 20, 32, 52, 4, 12);
        copy(image, 52, 20, 44, 52, 4, 12);
    }

    private static void copy(BufferedImage image, int sx, int sy, int dx, int dy, int w, int h) {
        int[] block = new int[w * h];
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) block[j * w + i] = image.getRGB(sx + i, sy + j);
        }
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) image.setRGB(dx + i, dy + j, block[j * w + (w - 1 - i)]);
        }
    }

    /** Repeats the middle column of each 3px face so a slim arm fills a 4px arm. */
    private static BufferedImage widenSlimArms(BufferedImage source) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        out.setData(source.getData());
        for (int[] arm : new int[][]{{40, 16}, {40, 32}, {32, 48}, {48, 48}}) {
            int bx = arm[0], by = arm[1];
            for (int y = by; y < by + 16; y++) {
                for (int x = bx; x < bx + 16; x++) out.setRGB(x, y, 0);
            }
            widen(source, out, bx + 4, bx + 4, by, 4, false);
            widen(source, out, bx + 7, bx + 8, by, 4, false);
            int[] from = {bx, bx + 4, bx + 7, bx + 11};
            int[] widths = {4, 3, 4, 3};
            int to = bx;
            for (int face = 0; face < 4; face++) {
                boolean narrow = widths[face] == 3;
                widen(source, out, from[face], to, by + 4, 12, !narrow);
                to += 4;
            }
        }
        return out;
    }

    private static void widen(BufferedImage source, BufferedImage out, int sx, int dx, int y, int h, boolean alreadyWide) {
        int[] columns = alreadyWide ? new int[]{0, 1, 2, 3} : new int[]{0, 1, 1, 2};
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < h; j++) out.setRGB(dx + i, y + j, source.getRGB(sx + columns[i], y + j));
        }
    }

    /** Clears the face and hair. With a hat, its top and the top rows of each side stay. */
    private static void clearHead(BufferedImage image, int hatRows) {
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 64; x++) {
                boolean base = x < 32;
                boolean keep = hatRows > 0 && (!base || (y < 8 ? x >= 8 && x < 16 : y < 8 + hatRows));
                if (!keep) image.setRGB(x, y, 0);
            }
        }
    }

    private static void stripSkin(BufferedImage image, int x0, int y0, int w, int h) {
        float[] hsb = new float[3];
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                int argb = image.getRGB(x, y);
                if (alpha(argb) == 0) continue;
                Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, hsb);
                float hue = hsb[0] * 360f;
                if (hue >= 8 && hue <= 48 && hsb[1] >= 0.10f && hsb[1] <= 0.70f && hsb[2] >= 0.40f) image.setRGB(x, y, 0);
            }
        }
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xFF;
    }
}
