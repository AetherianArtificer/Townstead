package com.aetherianartificer.townstead.client.catalog;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * What to draw for one building requirement, and what to call it.
 *
 * <p>Most requirements are an item and draw as one. Water and lava have no item form, so
 * {@code asItem()} answers air and drawing an item stack shows nothing at all. Those draw from
 * the block atlas instead, which is what a player sees in the world.</p>
 */
public record RequirementIcon(ItemStack stack, @Nullable TextureAtlasSprite sprite, int tint, String label) {
    public static final RequirementIcon EMPTY = new RequirementIcon(ItemStack.EMPTY, null, 0xFFFFFFFF, "");

    public static RequirementIcon of(ItemStack stack, String label) {
        return new RequirementIcon(stack, null, 0xFFFFFFFF, label);
    }

    public static RequirementIcon of(TextureAtlasSprite sprite, int tint, String label) {
        return new RequirementIcon(ItemStack.EMPTY, sprite, tint, label);
    }

    public boolean isEmpty() {
        return stack.isEmpty() && sprite == null;
    }

    /**
     * Draws the icon at 16x16, scaled by the caller's pose. An item goes through the item
     * renderer so its model and any overlay stay correct; a sprite is blitted with its tint,
     * which is what gives water the colour of the biome the player is standing in.
     */
    public void render(GuiGraphics graphics, int x, int y) {
        if (!stack.isEmpty()) {
            graphics.renderItem(stack, x, y);
            return;
        }
        if (sprite == null) return;
        float alpha = (tint >> 24 & 0xFF) / 255.0f;
        graphics.blit(x, y, 0, 16, 16, sprite,
                (tint >> 16 & 0xFF) / 255.0f, (tint >> 8 & 0xFF) / 255.0f, (tint & 0xFF) / 255.0f,
                alpha == 0.0f ? 1.0f : alpha);
    }
}
