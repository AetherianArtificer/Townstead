package com.aetherianartificer.townstead.clothing;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One thing an entity is wearing right now, as the engine sees it: which layer, which channel,
 * and either a stack or a skin id. A record source with neither (a hat that is not an item)
 * reports the entry it resolved to and nothing else.
 */
public record WornPiece(ClothingLayer layer,
                        @Nullable ClothingChannel channel,
                        @Nullable ItemStack stack,
                        @Nullable String skin,
                        @Nullable ClothingEntry entry,
                        String source) {

    public static WornPiece ofStack(ClothingLayer layer, @Nullable ClothingChannel channel, ItemStack stack,
                                    @Nullable ClothingEntry entry, String source) {
        return new WornPiece(layer, channel, stack, null, entry, source);
    }

    public static WornPiece ofSkin(String skin, @Nullable ClothingEntry entry, String source) {
        return new WornPiece(ClothingLayer.BASE, ClothingChannel.ALL, null, skin, entry, source);
    }

    public static WornPiece ofRecord(ClothingLayer layer, @Nullable ClothingChannel channel,
                                     @Nullable ClothingEntry entry, String source) {
        return new WornPiece(layer, channel, null, null, entry, source);
    }

    public boolean isStack() {
        return stack != null && !stack.isEmpty();
    }

    public boolean isSkin() {
        return skin != null && !skin.isEmpty();
    }
}
