package com.aetherianartificer.townstead.client.gui.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

/**
 * How the dialogue screen looks, resolved from the layered theme files (see {@link DialogueThemes}).
 * Colors are ARGB. A part with a {@link Frame} draws that texture instead of its color fills.
 */
public record DialogueTheme(
        @Nullable Frame box,
        int boxBackground, int boxBorderLight, int boxBorderDark, int boxInnerLight, int boxInnerDark,
        int boxAccent, int boxCorner,
        @Nullable Frame nameTab,
        int nameTabBackground, int nameColor,
        int textColor, int indicatorColor,
        @Nullable Frame choices,
        int choicesBackground, int choicesBorderLight, int choicesBorderDark, int choiceColor,
        int choiceHoverColor, int choiceSelectedBackground, int choiceBackColor, int choiceNumberColor,
        SoundEvent typeSound
) {
    /** A stretched nine-slice texture: {@code slice} pixels at each edge keep their size. */
    public record Frame(ResourceLocation texture, int textureWidth, int textureHeight, int slice) {
        public void draw(GuiGraphics g, int x, int y, int w, int h, float alpha) {
            int s = Math.min(slice, Math.min(w, h) / 2);
            int tw = textureWidth, th = textureHeight, ts = slice;
            int midW = w - s * 2, midH = h - s * 2, texMidW = tw - ts * 2, texMidH = th - ts * 2;
            g.setColor(1f, 1f, 1f, alpha);
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            // corners
            g.blit(texture, x, y, s, s, 0, 0, ts, ts, tw, th);
            g.blit(texture, x + w - s, y, s, s, tw - ts, 0, ts, ts, tw, th);
            g.blit(texture, x, y + h - s, s, s, 0, th - ts, ts, ts, tw, th);
            g.blit(texture, x + w - s, y + h - s, s, s, tw - ts, th - ts, ts, ts, tw, th);
            // edges and middle
            if (midW > 0) {
                g.blit(texture, x + s, y, midW, s, ts, 0, texMidW, ts, tw, th);
                g.blit(texture, x + s, y + h - s, midW, s, ts, th - ts, texMidW, ts, tw, th);
            }
            if (midH > 0) {
                g.blit(texture, x, y + s, s, midH, 0, ts, ts, texMidH, tw, th);
                g.blit(texture, x + w - s, y + s, s, midH, tw - ts, ts, ts, texMidH, tw, th);
            }
            if (midW > 0 && midH > 0) g.blit(texture, x + s, y + s, midW, midH, ts, ts, texMidW, texMidH, tw, th);
            g.setColor(1f, 1f, 1f, 1f);
        }
    }

    /** Reads a merged theme object; anything missing or malformed takes the classic value. */
    static DialogueTheme parse(JsonObject json) {
        JsonObject box = object(json, "box");
        JsonObject tab = object(json, "name_tab");
        JsonObject choices = object(json, "choices");
        ResourceLocation sound = id(json, "type_sound");
        return new DialogueTheme(
                frame(box),
                color(box, "background", 0xCC0E0E0E), color(box, "border_light", 0xFFA0A0A0),
                color(box, "border_dark", 0xFF373737), color(box, "inner_light", 0xFF606060),
                color(box, "inner_dark", 0xFF252525), color(box, "accent", 0xFF707070),
                color(box, "corner", 0xFFB0B0B0),
                frame(tab),
                color(tab, "background", 0xDD0E0E0E), color(tab, "name_color", 0xFFFFD700),
                color(json, "text_color", 0xFFFFFFFF), color(json, "indicator_color", 0xFFFFFFFF),
                frame(choices),
                color(choices, "background", 0xAA000000), color(choices, "border_light", 0xFF888888),
                color(choices, "border_dark", 0xFF555555), color(choices, "text_color", 0xAAFFFFFF),
                color(choices, "hover_color", 0xFFD7D784), color(choices, "selected_background", 0x44FFFFFF),
                color(choices, "back_color", 0xFF8888AA), color(choices, "number_color", 0x88FFFFFF),
                sound == null ? SoundEvents.WOODEN_BUTTON_CLICK_ON : SoundEvent.createVariableRangeEvent(sound));
    }

    private static JsonObject object(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static @Nullable ResourceLocation id(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        return ResourceLocation.tryParse(json.get(key).getAsString());
    }

    private static @Nullable Frame frame(JsonObject part) {
        ResourceLocation texture = id(part, "texture");
        if (texture == null || net.minecraft.client.Minecraft.getInstance().getResourceManager()
                .getResource(texture).isEmpty()) return null;
        int w = 32, h = 32;
        if (part.has("texture_size") && part.get("texture_size").isJsonArray()) {
            JsonArray size = part.getAsJsonArray("texture_size");
            if (size.size() == 2) {
                w = size.get(0).getAsInt();
                h = size.get(1).getAsInt();
            }
        }
        int slice = part.has("slice") ? part.get("slice").getAsInt() : 4;
        if (w <= 0 || h <= 0 || slice < 0 || slice * 2 > Math.min(w, h)) return null;
        return new Frame(texture, w, h, slice);
    }

    /** "#RRGGBB" (opaque) or "#AARRGGBB". */
    private static int color(JsonObject json, String key, int fallback) {
        JsonElement raw = json.get(key);
        if (raw == null || !raw.isJsonPrimitive()) return fallback;
        String s = raw.getAsString().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return 0xFF000000 | Integer.parseUnsignedInt(s, 16);
            if (s.length() == 8) return (int) Long.parseLong(s, 16);
        } catch (NumberFormatException ignored) {
        }
        return fallback;
    }
}
