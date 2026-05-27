package com.aetherianartificer.townstead.client.gui.origin;

import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.compat.thirst.ThirstCompatBridge;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;

/**
 * Shared drawing + color helpers for gene chips, reused by the picker's traits
 * list and the dedicated gene screen so the visual language stays identical:
 * per-category tints, colour-palette gradient swatches, range bars, the
 * solid/dashed dominance border, the tinted stone-button backdrop, and the
 * per-category icons.
 */
final class GeneVisuals {

    private GeneVisuals() {}

    /** Pixel size of a category icon (matches the 9px vanilla HUD sprites). */
    static final int ICON_SIZE = 9;

    //? if >=1.21 {
    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png");
    private static final ResourceLocation FOOD_ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/food_full.png");
    private static final ResourceLocation HEART_ICON = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/hud/heart/full.png");
    private static final ResourceLocation ENERGY_ICON = ResourceLocation.fromNamespaceAndPath("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation STEVE_SKIN = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/entity/player/wide/steve.png");
    //?} else {
    /*private static final ResourceLocation STONE = new ResourceLocation("minecraft", "textures/block/stone.png");
    private static final ResourceLocation VANILLA_ICONS = new ResourceLocation("minecraft", "textures/gui/icons.png");
    private static final ResourceLocation ENERGY_ICON = new ResourceLocation("townstead", "textures/gui/energy_full.png");
    private static final ResourceLocation STEVE_SKIN = new ResourceLocation("minecraft", "textures/entity/player/wide/steve.png");
    *///?}

    // Lifespan / Abilities have no vanilla HUD sprite, so they render as item icons.
    private static final ItemStack LIFESPAN_ITEM = new ItemStack(Items.CLOCK);
    private static final ItemStack ABILITIES_ITEM = new ItemStack(Items.FEATHER);

    /**
     * A tinted, pressed-in stone button backdrop: a tiled stone texture darkened for
     * contrast, washed with the category tint, and finished with an inset bevel (dark
     * top/left, light bottom/right) one pixel in from the edge so the dominance frame
     * can still ride the outer edge.
     */
    static void drawStoneButton(GuiGraphics g, int x0, int y0, int x1, int y1, int tint, boolean hovered) {
        int h = Math.min(16, y1 - y0);
        for (int tx = x0; tx < x1; tx += 16) {
            int w = Math.min(16, x1 - tx);
            g.blit(STONE, tx, y0, 0, 0, w, h, 16, 16);
        }
        g.fill(x0, y0, x1, y1, 0x55000000);                       // darken stone for legibility
        g.fill(x0, y0, x1, y1, (0x55 << 24) | (tint & 0xFFFFFF)); // category tint wash
        if (hovered) g.fill(x0, y0, x1, y1, 0x28FFFFFF);          // hover brighten
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, 0x90000000);       // bevel top (dark)
        g.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, 0x90000000);       // bevel left (dark)
        g.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, 0x55FFFFFF);       // bevel bottom (light)
        g.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, 0x55FFFFFF);       // bevel right (light)
    }

    /**
     * Whether this category renders as an icon. Hydration only does so when a thirst mod
     * is present (its icon comes from that mod, and the gene is otherwise inert).
     */
    static boolean hasCategoryIcon(String cat) {
        switch (cat == null ? "" : cat.toLowerCase(Locale.ROOT)) {
            case "diet": case "health": case "lifespan": case "abilities":
            case "activity": case "appearance": return true;
            case "hydration": return ThirstBridgeResolver.isActive();
            default: return false;
        }
    }

    /** Draws the {@link #ICON_SIZE}px category icon at (x,y). Only call when {@link #hasCategoryIcon}. */
    static void drawCategoryIcon(GuiGraphics g, String cat, int x, int y) {
        switch (cat == null ? "" : cat.toLowerCase(Locale.ROOT)) {
            case "diet":       foodIcon(g, x, y); break;
            case "health":     heartIcon(g, x, y); break;
            case "lifespan":   itemIcon(g, LIFESPAN_ITEM, x, y); break;
            case "abilities":  itemIcon(g, ABILITIES_ITEM, x, y); break;
            case "activity":   g.blit(ENERGY_ICON, x, y, 0, 0, 9, 9, 9, 9); break;
            case "appearance": g.blit(STEVE_SKIN, x, y, 8, 8, 8, 8, 64, 64); break; // default-skin face
            case "hydration":  thirstIcon(g, x, y); break;
            default: break;
        }
    }

    private static void foodIcon(GuiGraphics g, int x, int y) {
        //? if >=1.21 {
        g.blit(FOOD_ICON, x, y, 0, 0, 9, 9, 9, 9);
        //?} else {
        /*g.blit(VANILLA_ICONS, x, y, 52, 27, 9, 9, 256, 256);
        *///?}
    }

    private static void heartIcon(GuiGraphics g, int x, int y) {
        //? if >=1.21 {
        g.blit(HEART_ICON, x, y, 0, 0, 9, 9, 9, 9);
        //?} else {
        /*g.blit(VANILLA_ICONS, x, y, 52, 0, 9, 9, 256, 256);
        *///?}
    }

    private static void thirstIcon(GuiGraphics g, int x, int y) {
        ThirstCompatBridge bridge = ThirstBridgeResolver.get();
        if (bridge == null) return;
        ThirstCompatBridge.ThirstIconInfo i = bridge.iconInfo(20); // full droplet
        g.blit(i.texture(), x, y, i.u(), i.v(), 9, 9, i.texW(), i.texH());
    }

    /** Renders a 16px item icon scaled into the category icon slot (a touch larger reads better). */
    private static void itemIcon(GuiGraphics g, ItemStack stack, int x, int y) {
        float s = (ICON_SIZE + 1) / 16f;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(s, s, 1f);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    static int categoryTint(String cat) {
        switch (cat == null ? "" : cat.toLowerCase(Locale.ROOT)) {
            case "diet":       return 0xFF8FBF6F;
            case "hydration":  return 0xFF6FA8D8;
            case "activity":   return 0xFFD8B45A;
            case "health":     return 0xFFCF7070;
            case "lifespan":   return 0xFFB0AEC8;
            case "abilities":  return 0xFFD58CB8;
            case "appearance": return 0xFF6FCFC0;
            case "genetics":   return 0xFFB58CD8;
            default:           return hashTint(cat);
        }
    }

    /** Stable mid-bright tint from a string (unknown categories / per-locus colours). */
    static int hashTint(String s) {
        int h = (s == null ? 0 : s.hashCode());
        float hue = ((h & 0x7FFFFFFF) % 360) / 360f;
        return hsv(hue, 0.45f, 0.82f);
    }

    /** A gradient swatch between two RGB endpoints. */
    static void drawSwatch(GuiGraphics g, int from, int to, int x, int y, int w, int h) {
        for (int i = 0; i < w; i++) {
            float frac = w <= 1 ? 0f : i / (float) (w - 1);
            int col = lerp(from, to, frac);
            g.fill(x + i, y, x + i + 1, y + h, 0xFF000000 | (col & 0xFFFFFF));
        }
        g.fill(x - 1, y - 1, x + w + 1, y, 0xFF101010);
        g.fill(x - 1, y + h, x + w + 1, y + h + 1, 0xFF101010);
        g.fill(x - 1, y, x, y + h, 0xFF101010);
        g.fill(x + w, y, x + w + 1, y + h, 0xFF101010);
    }

    /** A 0-1 track with the [min,max] band filled. */
    static void drawRangeBar(GuiGraphics g, float min, float max, int x, int y, int w, int h, int fill) {
        g.fill(x, y, x + w, y + h, 0xFF0A0A0A);
        int bandL = x + Math.round(min * w);
        int bandR = x + Math.round(max * w);
        g.fill(bandL, y, Math.max(bandL + 1, bandR), y + h, fill);
    }

    static void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2, int color, boolean dashed) {
        if (!dashed) {
            g.fill(x1, y1, x2, y1 + 1, color);
            g.fill(x1, y2 - 1, x2, y2, color);
            g.fill(x1, y1, x1 + 1, y2, color);
            g.fill(x2 - 1, y1, x2, y2, color);
            return;
        }
        for (int x = x1; x < x2; x += 4) {
            int e = Math.min(x + 2, x2);
            g.fill(x, y1, e, y1 + 1, color);
            g.fill(x, y2 - 1, e, y2, color);
        }
        for (int y = y1; y < y2; y += 4) {
            int e = Math.min(y + 2, y2);
            g.fill(x1, y, x1 + 1, e, color);
            g.fill(x2 - 1, y, x2, e, color);
        }
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    static int scale(int c, float f) {
        return ((int) (((c >> 16) & 0xFF) * f) << 16)
                | ((int) (((c >> 8) & 0xFF) * f) << 8)
                | (int) ((c & 0xFF) * f);
    }

    static int hsv(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0:  r = v; g = t; b = p; break;
            case 1:  r = q; g = v; b = p; break;
            case 2:  r = p; g = v; b = t; break;
            case 3:  r = p; g = q; b = v; break;
            case 4:  r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    static String truncate(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) return s;
        int ellW = font.width("…");
        StringBuilder out = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            int cw = font.width(String.valueOf(s.charAt(i)));
            if (w + cw + ellW > maxW) break;
            out.append(s.charAt(i));
            w += cw;
        }
        return out.append("…").toString();
    }

    static String fmt(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    static String shortId(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    static String prettify(String id) {
        String s = shortId(id).replace('_', ' ');
        if (s.isEmpty()) return s;
        StringBuilder out = new StringBuilder();
        for (String word : s.split(" ")) {
            if (word.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
