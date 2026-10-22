package com.aetherianartificer.townstead.pheno.particle;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A data-pack particle: {@code data/<ns>/particle/<id>.json}. Its sprite is a data-pack texture
 * ({@code data/<ns>/textures/...png}, synced to clients like attachment textures), so a pack ships
 * particles with no resource pack and no code.
 *
 * <pre>
 * {
 *   "texture": "my_pack:textures/particle/frost_mote.png",
 *   "frames": 4,            sprite strip, stacked vertically (default 1)
 *   "frame_time": 3,        ticks per frame; 0 spreads the frames over the particle's life
 *   "lifetime": [20, 40],   ticks, a number or [min, max]
 *   "size": [0.08, 0.14],   blocks, a number or [min, max]
 *   "colors": ["#BFE8FF", "#FFFFFF"],  tint, one picked per particle (default white)
 *   "alpha": 0.9,
 *   "glow": true,           full-bright, ignoring world light
 *   "gravity": -0.01,       negative rises
 *   "drag": 0.96,           velocity kept per tick
 *   "fade_in": 4, "fade_out": 10,   ticks
 *   "shrink": true,         size falls to nothing over its life
 *   "spin": 6,              degrees per tick, random direction
 *   "flicker": 0.3,         random alpha loss per tick, 0-1
 *   "collide": false,
 *   "sway": 0.0015,         sideways drift pushed each tick, like a wisp or a falling leaf
 *   "sway_rate": 0.35,      how fast the sway turns
 *   "pulse": 0.4,           size swells and shrinks by this fraction
 *   "pulse_rate": 1.1,
 *   "tumble": 0.6,          rocks back and forth by this many radians instead of spinning
 *   "flare": 1.6,           size multiplier for the last fifth of its life
 *   "fade_out_fraction": 0.34   fade over this share of its life (instead of fade_out ticks)
 * }
 * </pre>
 */
public record DataParticleDef(String texture, int frames, int frameTime, int lifetimeMin, int lifetimeMax,
                              float sizeMin, float sizeMax, int[] colors, float alpha, boolean glow,
                              float gravity, float drag, int fadeIn, int fadeOut, boolean shrink,
                              float spin, float flicker, boolean collide, float sway, float swayRate,
                              float pulse, float pulseRate, float tumble, float flare, float fadeOutFraction) {

    public static @Nullable DataParticleDef parse(JsonObject json) {
        String texture = GsonHelper.getAsString(json, "texture", "");
        if (texture.isEmpty()) return null;
        int[] lifetime = intRange(json.get("lifetime"), 20);
        float[] size = floatRange(json.get("size"), 0.1f);
        return new DataParticleDef(texture,
                Math.max(1, GsonHelper.getAsInt(json, "frames", 1)),
                Math.max(0, GsonHelper.getAsInt(json, "frame_time", 0)),
                Math.max(1, lifetime[0]), Math.max(1, lifetime[1]),
                Math.max(0.005f, size[0]), Math.max(0.005f, size[1]),
                colors(json.get("colors") != null ? json.get("colors") : json.get("color")),
                clamp01(GsonHelper.getAsFloat(json, "alpha", 1f)),
                GsonHelper.getAsBoolean(json, "glow", false),
                GsonHelper.getAsFloat(json, "gravity", 0f),
                clamp01(GsonHelper.getAsFloat(json, "drag", 0.96f)),
                Math.max(0, GsonHelper.getAsInt(json, "fade_in", 0)),
                Math.max(0, GsonHelper.getAsInt(json, "fade_out", 0)),
                GsonHelper.getAsBoolean(json, "shrink", false),
                GsonHelper.getAsFloat(json, "spin", 0f),
                clamp01(GsonHelper.getAsFloat(json, "flicker", 0f)),
                GsonHelper.getAsBoolean(json, "collide", false),
                GsonHelper.getAsFloat(json, "sway", 0f),
                GsonHelper.getAsFloat(json, "sway_rate", 0.3f),
                Math.max(0f, GsonHelper.getAsFloat(json, "pulse", 0f)),
                GsonHelper.getAsFloat(json, "pulse_rate", 1f),
                GsonHelper.getAsFloat(json, "tumble", 0f),
                Math.max(0f, GsonHelper.getAsFloat(json, "flare", 1f)),
                clamp01(GsonHelper.getAsFloat(json, "fade_out_fraction", 0f)));
    }

    private static int[] intRange(@Nullable JsonElement value, int fallback) {
        if (value == null) return new int[]{fallback, fallback};
        if (value.isJsonArray() && value.getAsJsonArray().size() >= 2) {
            int a = value.getAsJsonArray().get(0).getAsInt(), b = value.getAsJsonArray().get(1).getAsInt();
            return new int[]{Math.min(a, b), Math.max(a, b)};
        }
        int v = value.getAsInt();
        return new int[]{v, v};
    }

    private static float[] floatRange(@Nullable JsonElement value, float fallback) {
        if (value == null) return new float[]{fallback, fallback};
        if (value.isJsonArray() && value.getAsJsonArray().size() >= 2) {
            float a = value.getAsJsonArray().get(0).getAsFloat(), b = value.getAsJsonArray().get(1).getAsFloat();
            return new float[]{Math.min(a, b), Math.max(a, b)};
        }
        float v = value.getAsFloat();
        return new float[]{v, v};
    }

    private static int[] colors(@Nullable JsonElement value) {
        List<Integer> out = new ArrayList<>();
        if (value != null && value.isJsonArray()) {
            for (JsonElement e : (JsonArray) value) {
                Integer rgb = rgb(e.getAsString());
                if (rgb != null) out.add(rgb);
            }
        } else if (value != null && value.isJsonPrimitive()) {
            Integer rgb = rgb(value.getAsString());
            if (rgb != null) out.add(rgb);
        }
        if (out.isEmpty()) out.add(0xFFFFFF);
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    /** {@code #RRGGBB} or {@code RRGGBB} to packed RGB; null when unreadable. */
    public static @Nullable Integer rgb(String spec) {
        if (spec == null) return null;
        String hex = spec.trim().toLowerCase(Locale.ROOT);
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6) return null;
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
