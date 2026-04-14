package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves block texture sprites from the block atlas for top-down cell rendering.
 * Cached per run because sprite lookups are cheap but repeated lookups each frame aren't.
 */
public final class CellTextures {
    private CellTextures() {}

    private static final Map<String, TextureAtlasSprite> CACHE = new HashMap<>();

    public static TextureAtlasSprite get(String texturePath) {
        return CACHE.computeIfAbsent(texturePath, path -> {
            //? if >=1.21 {
            ResourceLocation rl = ResourceLocation.parse(path);
            //?} else {
            /*ResourceLocation rl = new ResourceLocation(path);
            *///?}
            return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(rl);
        });
    }

    public static void blit(GuiGraphics g, String texturePath, int x, int y, int size) {
        TextureAtlasSprite sprite = get(texturePath);
        g.blit(x, y, 0, size, size, sprite);
    }

    public static void invalidate() {
        CACHE.clear();
    }
}
