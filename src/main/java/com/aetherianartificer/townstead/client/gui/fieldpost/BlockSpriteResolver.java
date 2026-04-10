package com.aetherianartificer.townstead.client.gui.fieldpost;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves any BlockState to a top-face sprite + biome tint color.
 * Cached per BlockState since sprite lookups are cheap but per-frame recompute wastes time.
 */
public final class BlockSpriteResolver {
    private BlockSpriteResolver() {}

    public record Resolved(TextureAtlasSprite sprite, int tint) {}

    private static final Map<BlockState, TextureAtlasSprite> SPRITE_CACHE = new HashMap<>();

    /**
     * Get the top-face sprite of a block state. Falls back to the particle icon if no UP face exists.
     */
    public static TextureAtlasSprite getTopSprite(BlockState state) {
        TextureAtlasSprite cached = SPRITE_CACHE.get(state);
        if (cached != null) return cached;

        Minecraft mc = Minecraft.getInstance();
        BakedModel model;
        try {
            model = mc.getBlockRenderer().getBlockModel(state);
        } catch (Throwable t) {
            return null;
        }
        TextureAtlasSprite sprite = null;
        try {
            List<BakedQuad> upQuads = model.getQuads(state, Direction.UP, RandomSource.create());
            if (!upQuads.isEmpty()) {
                sprite = upQuads.get(0).getSprite();
            }
        } catch (Throwable ignored) {}

        if (sprite == null) {
            try {
                sprite = model.getParticleIcon();
            } catch (Throwable ignored) {}
        }

        if (sprite != null) SPRITE_CACHE.put(state, sprite);
        return sprite;
    }

    /**
     * Get the biome tint color for a block state at the given position (grass, leaves, water).
     * Returns 0xFFFFFFFF (white = no tint) for blocks without a color provider.
     */
    public static int getTint(BlockState state, Level level, BlockPos pos) {
        try {
            int color = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            if (color == -1) return 0xFFFFFFFF;
            // Block colors return RGB; add full alpha
            return 0xFF000000 | color;
        } catch (Throwable t) {
            return 0xFFFFFFFF;
        }
    }

    public static void invalidate() {
        SPRITE_CACHE.clear();
    }
}
