package com.aetherianartificer.townstead.root.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Additive presentation metadata for a {@code pheno:resource}. Gameplay state remains in
 * {@link ResourceGeneType.Instance}; this record only describes how and when the owning
 * client's HUD should present it.
 */
public record ResourceDisplay(
        Shape shape,
        FillMode fillMode,
        PipStyle pipStyle,
        ResourceLocation frame,
        ResourceLocation colorTheme,
        List<BarEffect> effects,
        Anchor anchor,
        Eligibility eligibility,
        int segments,
        int priority
) {

    /** One entry in the ordered bar-effect stack. */
    public record BarEffect(ResourceLocation type, float strength, String gradientShape,
                            int highlightColor, int shadowColor) {
        public BarEffect {
            type = type == null ? id("townstead:none", "townstead:none") : type;
            strength = Math.max(0f, Math.min(1f, strength));
            gradientShape = gradientShape == null || gradientShape.isBlank()
                    ? "crosswise" : normalize(gradientShape);
            highlightColor = highlightColor < 0 ? -1 : highlightColor & 0xFFFFFF;
            shadowColor = shadowColor < 0 ? -1 : shadowColor & 0xFFFFFF;
        }
    }

    private record GradientPreset(float strength, String shape) {}

    public enum Shape {
        HORIZONTAL, VERTICAL, SQUIRCLE;

        static Shape parse(String raw) {
            return switch (normalize(raw)) {
                case "vertical" -> VERTICAL;
                case "squircle", "ring", "circle", "circular", "radial" -> SQUIRCLE;
                default -> HORIZONTAL;
            };
        }
    }

    /** Small, discrete marks used by {@link FillMode#PIPS}; never stretched bar segments. */
    public enum PipStyle {
        DOTS, NOTCHES, BEADS, SHARDS;

        static PipStyle parse(String raw) {
            return switch (normalize(raw)) {
                case "notch", "notches", "ticks" -> NOTCHES;
                case "bead", "beads", "pearls" -> BEADS;
                case "shard", "shards", "crystals" -> SHARDS;
                default -> DOTS;
            };
        }
    }

    public enum FillMode {
        CONTINUOUS, SEGMENTED, PIPS;

        static FillMode parse(String raw) {
            return switch (normalize(raw)) {
                case "segmented", "segments" -> SEGMENTED;
                case "pips", "separated", "separate" -> PIPS;
                default -> CONTINUOUS;
            };
        }
    }

    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

        static Anchor parse(String raw) {
            try {
                return Anchor.valueOf(normalize(raw).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return TOP_LEFT;
            }
        }
    }

    /** Whether an expressed meter is eligible to enter the HUD at all. */
    public enum Eligibility {
        WHEN_EXPRESSED, WHEN_REFERENCED, NEVER;

        static Eligibility parse(String raw) {
            return switch (normalize(raw)) {
                case "when_referenced", "referenced", "consumer" -> WHEN_REFERENCED;
                case "never", "hidden" -> NEVER;
                default -> WHEN_EXPRESSED;
            };
        }
    }

    public static ResourceDisplay parse(JsonObject resource) {
        JsonObject display = resource.has("display") && resource.get("display").isJsonObject()
                ? resource.getAsJsonObject("display") : new JsonObject();
        Shape shape = Shape.parse(GsonHelper.getAsString(display, "shape", "horizontal"));
        FillMode fill = FillMode.parse(GsonHelper.getAsString(display, "fill_mode", "continuous"));
        PipStyle pipStyle = PipStyle.parse(GsonHelper.getAsString(display, "pip_style", "dots"));
        ResourceLocation frame = id(GsonHelper.getAsString(display, "frame", "townstead:plain"),
                "townstead:plain");
        String colorThemeRaw = GsonHelper.getAsString(display, "color_theme", "townstead:arcane");
        ResourceLocation colorTheme = id(colorThemeRaw,
                "townstead:arcane");
        List<BarEffect> effects = parseEffects(display);
        Anchor anchor = Anchor.parse(GsonHelper.getAsString(display, "anchor", "top_left"));
        Eligibility eligibility = Eligibility.parse(
                GsonHelper.getAsString(display, "visibility", "when_expressed"));
        int segments = Math.max(2, Math.min(64, GsonHelper.getAsInt(display, "segments", 10)));
        int priority = GsonHelper.getAsInt(display, "priority", 0);
        return new ResourceDisplay(shape, fill, pipStyle, frame, colorTheme, effects,
                anchor, eligibility, segments, priority);
    }

    private static List<BarEffect> parseEffects(JsonObject display) {
        if (!display.has("effects") || !display.get("effects").isJsonArray()) return List.of();
        List<BarEffect> effects = new ArrayList<>();
        for (JsonElement element : display.getAsJsonArray("effects")) {
            if (!element.isJsonObject()) {
                throw new IllegalArgumentException("Resource display effects must be objects");
            }
            JsonObject effect = element.getAsJsonObject();
            ResourceLocation type = id(GsonHelper.getAsString(effect, "type"), "townstead:none");
            if (!"townstead:none".equals(type.toString())
                    && !"townstead:gradient".equals(type.toString())) {
                throw new IllegalArgumentException("Unknown resource bar effect '" + type + "'");
            }
            if ("townstead:none".equals(type.toString())) continue;

            String presetName = normalize(GsonHelper.getAsString(effect, "preset", "standard"));
            GradientPreset preset = gradientPreset(presetName);
            float strength = GsonHelper.getAsFloat(effect, "strength", preset.strength());
            String shape = normalize(GsonHelper.getAsString(effect, "shape", preset.shape()));
            if (!List.of("crosswise", "along", "centered", "leading_edge", "radial").contains(shape)) {
                throw new IllegalArgumentException("Unknown gradient shape '" + shape + "'");
            }
            int highlight = optionalColor(effect, "highlight_color");
            int shadow = optionalColor(effect, "shadow_color");
            effects.add(new BarEffect(type, strength, shape, highlight, shadow));
        }
        return List.copyOf(effects);
    }

    private static GradientPreset gradientPreset(String name) {
        return switch (name) {
            case "subtle" -> new GradientPreset(0.15f, "crosswise");
            case "standard" -> new GradientPreset(0.30f, "crosswise");
            case "deep" -> new GradientPreset(0.50f, "crosswise");
            case "glossy" -> new GradientPreset(0.45f, "centered");
            case "leading_edge" -> new GradientPreset(0.40f, "leading_edge");
            default -> throw new IllegalArgumentException("Unknown gradient preset '" + name + "'");
        };
    }

    private static int optionalColor(JsonObject object, String key) {
        if (!object.has(key)) return -1;
        String raw = GsonHelper.getAsString(object, key, "");
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            if (hex.length() != 6) throw new NumberFormatException();
            return Integer.parseUnsignedInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected #RRGGBB for gradient " + key + ", got '" + raw + "'");
        }
    }

    private static ResourceLocation id(String raw, String fallback) {
        ResourceLocation parsed = DataPackLang.parseId(raw);
        return parsed != null ? parsed : DataPackLang.parseId(fallback);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }
}
