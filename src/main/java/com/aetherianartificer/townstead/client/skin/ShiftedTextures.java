package com.aetherianartificer.townstead.client.skin;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Skin-format textures moved by whole texels, for overlay art drawn for another face layout (a
 * vanilla Steve eye row laid over MCA's lower eyes). Offsets are in 64x64 texels and scale with
 * the texture. Baked once per texture and offset.
 */
public final class ShiftedTextures {
    private static final Map<String, ResourceLocation> BAKED = new HashMap<>();

    private ShiftedTextures() {}

    /** The shifted copy of {@code textureId}, or null when its pixels are not available yet. */
    public static @Nullable ResourceLocation get(String textureId, ResourceLocation source, int dx, int dy) {
        String key = textureId + "@" + dx + "," + dy;
        ResourceLocation cached = BAKED.get(key);
        if (cached != null) return cached;
        try (NativeImage image = read(textureId, source)) {
            if (image == null) return null;
            int scale = Math.max(1, image.getWidth() / 64);
            int sx = dx * scale;
            int sy = dy * scale;
            NativeImage shifted = new NativeImage(image.getWidth(), image.getHeight(), true);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int fromX = x - sx;
                    int fromY = y - sy;
                    boolean inside = fromX >= 0 && fromY >= 0 && fromX < image.getWidth() && fromY < image.getHeight();
                    shifted.setPixelRGBA(x, y, inside ? image.getPixelRGBA(fromX, fromY) : 0);
                }
            }
            if (BAKED.size() > 256) BAKED.clear();
            ResourceLocation id = ResourceLocation.tryParse(Townstead.MOD_ID + ":shifted/"
                    + Integer.toHexString(key.hashCode()) + "_" + (dx + 64) + "_" + (dy + 64));
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(shifted));
            BAKED.put(key, id);
            return id;
        } catch (Exception e) {
            Townstead.LOGGER.warn("Could not shift overlay texture {}: {}", textureId, e.getMessage());
            BAKED.put(key, source);
            return source;
        }
    }

    private static @Nullable NativeImage read(String textureId, ResourceLocation source) throws Exception {
        byte[] bytes = AttachmentClient.namedTextureBytes(textureId);
        if (bytes != null) return NativeImage.read(new ByteArrayInputStream(bytes));
        var resource = Minecraft.getInstance().getResourceManager().getResource(source);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open()) {
            return NativeImage.read(in);
        }
    }
}
