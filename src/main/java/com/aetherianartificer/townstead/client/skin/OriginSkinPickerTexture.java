package com.aetherianartificer.townstead.client.skin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a tinted copy of MCA's {@code villager_skin} colormap so the Body page's skin picker
 * square shows the origin's actual skin field (the same blend the skin-layer mixin applies to the
 * rendered villager) — WYSIWYG. Keyed by the packed tint the provider produces (see
 * {@link SkinBlend#pack}); results are cached as registered {@link DynamicTexture}s. White-multiply
 * (identity) returns MCA's original colormap so vanilla/Overworlder pickers are untouched.
 */
public final class OriginSkinPickerTexture {

    private OriginSkinPickerTexture() {}

    //? if >=1.21 {
    public static final ResourceLocation SKIN_COLORMAP =
            ResourceLocation.fromNamespaceAndPath("mca", "textures/colormap/villager_skin.png");
    //?} else {
    /*public static final ResourceLocation SKIN_COLORMAP =
            new ResourceLocation("mca", "textures/colormap/villager_skin.png");
    *///?}

    private static int[] srcPixels;   // ABGR, as NativeImage stores them
    private static int srcW, srcH;
    private static final Map<Integer, ResourceLocation> CACHE = new HashMap<>();

    /** Texture for the picker background given the provider's packed tint ({@link SkinBlend#pack}). */
    public static ResourceLocation forTint(int packed) {
        // White-multiply is the identity at any strength, so reuse MCA's untouched colormap.
        if (SkinBlend.packMode(packed) == 0 && SkinBlend.packTint(packed) == 0xFFFFFF) return SKIN_COLORMAP;
        ResourceLocation cached = CACHE.get(packed);
        if (cached != null) return cached;
        ResourceLocation rl = generate(packed);
        if (rl == null) return SKIN_COLORMAP;                     // source unavailable: leave it vanilla
        CACHE.put(packed, rl);
        return rl;
    }

    /**
     * Whether {@code rl} is the skin picker's background — MCA's colormap or one of our tinted
     * copies. Used to re-find the picker on revisits even after we've already swapped its texture.
     */
    public static boolean isSkinPickerTexture(ResourceLocation rl) {
        return SKIN_COLORMAP.equals(rl)
                || (rl != null && "townstead".equals(rl.getNamespace()) && rl.getPath().startsWith("origin_skin_picker/"));
    }

    private static ResourceLocation generate(int packed) {
        if (!loadSource()) return null;
        NativeImage img = new NativeImage(srcW, srcH, false);
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < srcW; x++) {
                int abgr = srcPixels[y * srcW + x];
                int a = (abgr >>> 24) & 0xFF;
                int baseRgb = ((abgr & 0xFF) << 16) | (((abgr >> 8) & 0xFF) << 8) | ((abgr >> 16) & 0xFF);
                int out = SkinBlend.blend(baseRgb, packed);
                int or = (out >> 16) & 0xFF, og = (out >> 8) & 0xFF, ob = out & 0xFF;
                img.setPixelRGBA(x, y, (a << 24) | (ob << 16) | (og << 8) | or);   // back to ABGR
            }
        }
        DynamicTexture tex = new DynamicTexture(img);
        //? if >=1.21 {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("townstead", "origin_skin_picker/" + Integer.toHexString(packed));
        //?} else {
        /*ResourceLocation rl = new ResourceLocation("townstead", "origin_skin_picker/" + Integer.toHexString(packed));
        *///?}
        Minecraft.getInstance().getTextureManager().register(rl, tex);
        return rl;
    }

    private static boolean loadSource() {
        if (srcPixels != null) return true;
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(SKIN_COLORMAP);
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
