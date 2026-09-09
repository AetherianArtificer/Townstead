package com.aetherianartificer.townstead.client.skin;

import com.aetherianartificer.townstead.root.appearance.HairColorChoice;
import com.aetherianartificer.townstead.root.appearance.HairColorRange;
import com.aetherianartificer.townstead.root.appearance.HairGradient;
import com.aetherianartificer.townstead.root.appearance.HairSettings;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * WYSIWYG backgrounds for the editor's hair colour controls under a Roots hair policy. A genetic
 * policy ({@code color_ranges}) keeps MCA's colormap, softened outside the allowed cells and with
 * each allowed region outlined; a fantasy gradient becomes a one-dimensional strip for MCA's
 * horizontal picker. Results are cached as registered {@link DynamicTexture}s.
 */
public final class RootHairPickerTexture {

    private RootHairPickerTexture() {}

    //? if >=1.21 {
    public static final ResourceLocation HAIR_COLORMAP =
            ResourceLocation.fromNamespaceAndPath("mca", "textures/colormap/villager_hair.png");
    //?} else {
    /*public static final ResourceLocation HAIR_COLORMAP =
            new ResourceLocation("mca", "textures/colormap/villager_hair.png");
    *///?}

    private static final int STRIP_WIDTH = 128;
    private static final int STRIP_HEIGHT = 4;
    /** Upscale so the region outline is one screen pixel wide, not one fat colormap cell. */
    private static final int RANGE_SCALE = 4;
    private static final float DIM_BRIGHTNESS = 0.5f;
    private static final float DIM_SATURATION = 0.5f;
    private static final float OUTLINE_MIX = 0.6f;

    private static int[] srcPixels;   // ABGR, as NativeImage stores them
    private static int srcW, srcH;
    private static final Map<Integer, ResourceLocation> RANGE_CACHE = new HashMap<>();
    private static final Map<HairGradient, ResourceLocation> GRADIENT_CACHE = new HashMap<>();

    /** Whether {@code rl} is the hair picker's background: MCA's colormap or one of our copies. */
    public static boolean isHairPickerTexture(ResourceLocation rl) {
        return HAIR_COLORMAP.equals(rl)
                || (rl != null && "townstead".equals(rl.getNamespace()) && rl.getPath().startsWith("root_hair_picker/"));
    }

    public static boolean hasPalette(HairSettings settings) {
        return settings != null && (!settings.colors().isEmpty() || !settings.gradients().isEmpty());
    }

    /** MCA's colormap with forbidden cells softened and every allowed region outlined. */
    public static ResourceLocation forRanges(List<HairColorRange> ranges) {
        if (ranges == null || ranges.isEmpty()) return HAIR_COLORMAP;
        int key = ranges.hashCode();
        ResourceLocation cached = RANGE_CACHE.get(key);
        if (cached != null) return cached;
        if (!loadSource()) return HAIR_COLORMAP;
        boolean[] allowed = new boolean[srcW * srcH];
        for (int y = 0; y < srcH; y++) {
            float darkness = srcH <= 1 ? 0f : y / (float) (srcH - 1);
            for (int x = 0; x < srcW; x++) {
                float redness = srcW <= 1 ? 0f : x / (float) (srcW - 1);
                for (HairColorRange range : ranges) {
                    if (range.distanceSquared(darkness, redness) == 0f) { allowed[y * srcW + x] = true; break; }
                }
            }
        }
        int w = srcW * RANGE_SCALE;
        int h = srcH * RANGE_SCALE;
        NativeImage img = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cell = (y / RANGE_SCALE) * srcW + x / RANGE_SCALE;
                int abgr = srcPixels[cell];
                if (!allowed[cell]) {
                    img.setPixelRGBA(x, y, soften(abgr));
                    continue;
                }
                boolean edge = !allowedAt(allowed, x - 1, y) || !allowedAt(allowed, x + 1, y)
                        || !allowedAt(allowed, x, y - 1) || !allowedAt(allowed, x, y + 1);
                img.setPixelRGBA(x, y, edge ? lighten(abgr) : abgr);
            }
        }
        ResourceLocation rl = register("ranges_" + Integer.toHexString(key), img);
        RANGE_CACHE.put(key, rl);
        return rl;
    }

    /** A horizontal strip traversing {@code gradient} left to right, for MCA's 1-D picker. */
    public static ResourceLocation forGradient(HairGradient gradient) {
        ResourceLocation cached = GRADIENT_CACHE.get(gradient);
        if (cached != null) return cached;
        NativeImage img = new NativeImage(STRIP_WIDTH, STRIP_HEIGHT, false);
        for (int x = 0; x < STRIP_WIDTH; x++) {
            int abgr = toAbgr(gradient.colorAt(x / (float) (STRIP_WIDTH - 1)));
            for (int y = 0; y < STRIP_HEIGHT; y++) img.setPixelRGBA(x, y, abgr);
        }
        ResourceLocation rl = register("gradient_" + Integer.toHexString(gradient.hashCode())
                + "_" + GRADIENT_CACHE.size(), img);
        GRADIENT_CACHE.put(gradient, rl);
        return rl;
    }

    /** Where a dye sits in the palette: a gradient index and position, or an exact colour index. */
    public record Pick(int gradient, float position, int color) {
        public boolean onGradient() { return gradient >= 0; }
    }

    /** The palette entry nearest {@code argb}, so the controls can show what the model renders. */
    public static Pick locate(HairSettings settings, int argb) {
        int bestGradient = -1;
        float bestT = 0f;
        int bestColor = -1;
        long best = Long.MAX_VALUE;
        List<HairGradient> gradients = settings.gradients();
        for (int g = 0; g < gradients.size(); g++) {
            HairGradient gradient = gradients.get(g);
            int samples = Math.max(64, (gradient.stops().size() - 1) * 64);
            for (int i = 0; i <= samples; i++) {
                float t = i / (float) samples;
                long distance = distance(gradient.colorAt(t), argb);
                if (distance < best) { best = distance; bestGradient = g; bestT = t; }
            }
        }
        List<HairColorChoice> colors = settings.colors();
        for (int i = 0; i < colors.size(); i++) {
            long distance = distance(colors.get(i).argb(), argb);
            if (distance < best) { best = distance; bestGradient = -1; bestColor = i; }
        }
        return new Pick(bestGradient, bestT, bestGradient >= 0 ? -1 : bestColor);
    }

    private static boolean allowedAt(boolean[] allowed, int x, int y) {
        if (x < 0 || y < 0 || x >= srcW * RANGE_SCALE || y >= srcH * RANGE_SCALE) return false;
        return allowed[(y / RANGE_SCALE) * srcW + x / RANGE_SCALE];
    }

    private static long distance(int a, int b) {
        int dr = ((a >> 16) & 255) - ((b >> 16) & 255);
        int dg = ((a >> 8) & 255) - ((b >> 8) & 255);
        int db = (a & 255) - (b & 255);
        return (long) dr * dr + (long) dg * dg + (long) db * db;
    }

    /** Desaturate toward the pixel's own luma, then darken: reads as "not here", not as a hole. */
    private static int soften(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        float b = (abgr >> 16) & 0xFF, g = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
        float luma = 0.299f * r + 0.587f * g + 0.114f * b;
        r = (luma + (r - luma) * DIM_SATURATION) * DIM_BRIGHTNESS;
        g = (luma + (g - luma) * DIM_SATURATION) * DIM_BRIGHTNESS;
        b = (luma + (b - luma) * DIM_SATURATION) * DIM_BRIGHTNESS;
        return (a << 24) | (Math.round(b) << 16) | (Math.round(g) << 8) | Math.round(r);
    }

    private static int lighten(int abgr) {
        int a = (abgr >>> 24) & 0xFF;
        int b = mixToWhite((abgr >> 16) & 0xFF);
        int g = mixToWhite((abgr >> 8) & 0xFF);
        int r = mixToWhite(abgr & 0xFF);
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int mixToWhite(int channel) {
        return Math.round(channel + (255 - channel) * OUTLINE_MIX);
    }

    private static int toAbgr(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (0xFF << 24) | (b << 16) | (g << 8) | r;
    }

    private static ResourceLocation register(String nameSuffix, NativeImage img) {
        //? if >=1.21 {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("townstead", "root_hair_picker/" + nameSuffix);
        //?} else {
        /*ResourceLocation rl = new ResourceLocation("townstead", "root_hair_picker/" + nameSuffix);
        *///?}
        Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
        return rl;
    }

    private static boolean loadSource() {
        if (srcPixels != null) return true;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(HAIR_COLORMAP);
        if (res.isEmpty()) return false;
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            srcW = img.getWidth();
            srcH = img.getHeight();
            srcPixels = new int[srcW * srcH];
            for (int y = 0; y < srcH; y++) {
                for (int x = 0; x < srcW; x++) {
                    srcPixels[y * srcW + x] = img.getPixelRGBA(x, y);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
