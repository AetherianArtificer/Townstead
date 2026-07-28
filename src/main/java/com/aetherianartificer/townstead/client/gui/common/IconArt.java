package com.aetherianartificer.townstead.client.gui.common;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Draws whatever a pack named as an icon: an item, or a texture of its own.
 *
 * <p>Icons arrive as free-form strings from the gene, skill and profession registries. Items only
 * was the limit, and it bit in both directions: an ability that applies a status effect wants that
 * effect's own sprite, and a pack shipping custom art had nowhere to point at it. A path ending in
 * {@code .png} is a texture; anything else is an item id, as before.</p>
 */
public final class IconArt {

    private IconArt() {}

    /** True when this icon names a texture rather than an item id. */
    public static boolean isTexture(String icon) {
        return icon != null && icon.endsWith(".png");
    }

    /** The item an icon names, or empty when it names a texture or an item that is not installed. */
    public static ItemStack stack(String icon) {
        if (icon == null || icon.isEmpty() || isTexture(icon)) return ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(icon);
        if (id == null) return ItemStack.EMPTY;
        return BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    /**
     * Draws a 16x16 icon scaled about its top-left corner, false when it named nothing drawable.
     *
     * <p>False is the caller's cue to fall back to initials, so an unauthored icon and an item from
     * a mod that is not installed keep behaving as they did.</p>
     *
     * <p>A texture is mapped WHOLE rather than sampled at an assumed size, so a pack can ship 16,
     * 18 or 32 pixels and land in the same cell. One that does not resolve draws Minecraft's missing
     * texture, which is the signal a pack author wants; there is no silent fallback to hide a typo
     * behind.</p>
     */
    public static boolean draw(GuiGraphics g, String icon, float x, float y, float scale) {
        if (icon == null || icon.isEmpty()) return false;
        if (isTexture(icon)) {
            ResourceLocation tex = ResourceLocation.tryParse(icon);
            if (tex == null) return false;
            int size = Math.max(1, Math.round(16 * scale));
            g.blit(tex, Math.round(x), Math.round(y), size, size, 0f, 0f, 1, 1, 1, 1);
            return true;
        }
        ItemStack stack = stack(icon);
        if (stack.isEmpty()) return false;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
        return true;
    }

    /** The same, centred on {@code cx, cy}. */
    public static boolean drawCentred(GuiGraphics g, String icon, float cx, float cy, float scale) {
        return draw(g, icon, cx - 8 * scale, cy - 8 * scale, scale);
    }
}
