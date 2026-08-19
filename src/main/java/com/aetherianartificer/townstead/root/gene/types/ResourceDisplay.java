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
    public record BarEffect(ResourceLocation type, float strength, float speed, float interval,
                            float frequency, int color,
                            String gradientShape, int highlightColor, int shadowColor,
                            int surfacePoints, float tension, float damping,
                            float splash, float movementInfluence,
                            int lobeCount, float viscosity, float stringiness,
                            int bubbleCount, int bubbleSize, float bubbleWobble,
                            int emberCount, float emberDrift, float emberFlicker,
                            float emberEscape) {
        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    12, 0.18f, 0.92f, 0.65f, 0.20f,
                    5, 0.78f, 0.55f,
                    6, 2, 0.35f,
                    8, 0.45f, 0.65f, 0.80f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    5, 0.78f, 0.55f,
                    6, 2, 0.35f,
                    8, 0.45f, 0.65f, 0.80f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    6, 2, 0.35f,
                    8, 0.45f, 0.65f, 0.80f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    8, 0.45f, 0.65f, 0.80f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, 0.80f);
        }

        public BarEffect {
            type = type == null ? id("townstead:none", "townstead:none") : type;
            strength = Math.max(0f, Math.min(1f, strength));
            speed = Math.max(0.05f, Math.min(4f, speed));
            interval = Math.max(0.5f, Math.min(30f, interval));
            frequency = Math.max(0.5f, Math.min(8f, frequency));
            color = color < 0 ? -1 : color & 0xFFFFFF;
            gradientShape = gradientShape == null || gradientShape.isBlank()
                    ? "crosswise" : normalize(gradientShape);
            highlightColor = highlightColor < 0 ? -1 : highlightColor & 0xFFFFFF;
            shadowColor = shadowColor < 0 ? -1 : shadowColor & 0xFFFFFF;
            surfacePoints = Math.max(8, Math.min(16, surfacePoints));
            tension = Math.max(0f, Math.min(1f, tension));
            damping = Math.max(0.5f, Math.min(0.995f, damping));
            splash = Math.max(0f, Math.min(2f, splash));
            movementInfluence = Math.max(0f, Math.min(1f, movementInfluence));
            lobeCount = Math.max(3, Math.min(8, lobeCount));
            viscosity = Math.max(0f, Math.min(0.98f, viscosity));
            stringiness = Math.max(0f, Math.min(1f, stringiness));
            bubbleCount = Math.max(1, Math.min(12, bubbleCount));
            bubbleSize = Math.max(1, Math.min(3, bubbleSize));
            bubbleWobble = Math.max(0f, Math.min(1f, bubbleWobble));
            emberCount = Math.max(1, Math.min(16, emberCount));
            emberDrift = Math.max(0f, Math.min(1f, emberDrift));
            emberFlicker = Math.max(0f, Math.min(1f, emberFlicker));
            emberEscape = Math.max(0f, Math.min(1f, emberEscape));
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
                    && !"townstead:gradient".equals(type.toString())
                    && !"townstead:shimmer".equals(type.toString())
                    && !"townstead:pulse".equals(type.toString())
                    && !"townstead:flow".equals(type.toString())
                    && !"townstead:liquid".equals(type.toString())
                    && !"townstead:viscous".equals(type.toString())
                    && !"townstead:bubbles".equals(type.toString())
                    && !"townstead:embers".equals(type.toString())) {
                throw new IllegalArgumentException("Unknown resource bar effect '" + type + "'");
            }
            if ("townstead:none".equals(type.toString())) continue;

            if ("townstead:shimmer".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.35f),
                        GsonHelper.getAsFloat(effect, "speed", 1f),
                        GsonHelper.getAsFloat(effect, "interval", 3.6f),
                        1f, optionalColor(effect, "color"), "crosswise", -1, -1));
                continue;
            }

            if ("townstead:pulse".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.25f),
                        GsonHelper.getAsFloat(effect, "speed", 1f),
                        3.6f, 1f, optionalColor(effect, "color"), "crosswise", -1, -1));
                continue;
            }

            if ("townstead:flow".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.30f),
                        GsonHelper.getAsFloat(effect, "speed", 1f),
                        3.6f, 1f, optionalColor(effect, "color"), "crosswise", -1, -1));
                continue;
            }

            if ("townstead:liquid".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.35f),
                        GsonHelper.getAsFloat(effect, "speed", 1f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        GsonHelper.getAsInt(effect, "surface_points", 12),
                        GsonHelper.getAsFloat(effect, "tension", 0.18f),
                        GsonHelper.getAsFloat(effect, "damping", 0.92f),
                        GsonHelper.getAsFloat(effect, "splash", 0.65f),
                        GsonHelper.getAsFloat(effect, "movement_influence", 0.20f)));
                continue;
            }

            if ("townstead:viscous".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.55f),
                        GsonHelper.getAsFloat(effect, "speed", 0.65f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        GsonHelper.getAsInt(effect, "lobes", 5),
                        GsonHelper.getAsFloat(effect, "viscosity", 0.78f),
                        GsonHelper.getAsFloat(effect, "stringiness", 0.55f)));
                continue;
            }

            if ("townstead:bubbles".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.65f),
                        GsonHelper.getAsFloat(effect, "speed", 0.85f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        GsonHelper.getAsInt(effect, "density", 6),
                        GsonHelper.getAsInt(effect, "size", 2),
                        GsonHelper.getAsFloat(effect, "wobble", 0.35f)));
                continue;
            }

            if ("townstead:embers".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.70f),
                        GsonHelper.getAsFloat(effect, "speed", 0.95f),
                        3.6f, 1f,
                        optionalColor(effect, "hot_color"), "crosswise", -1,
                        optionalColor(effect, "cool_color"),
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        GsonHelper.getAsInt(effect, "density", 8),
                        GsonHelper.getAsFloat(effect, "drift", 0.45f),
                        GsonHelper.getAsFloat(effect, "flicker", 0.65f),
                        GsonHelper.getAsFloat(effect, "escape", 0.80f)));
                continue;
            }

            String presetName = normalize(GsonHelper.getAsString(effect, "preset", "standard"));
            GradientPreset preset = gradientPreset(presetName);
            float strength = GsonHelper.getAsFloat(effect, "strength", preset.strength());
            String shape = normalize(GsonHelper.getAsString(effect, "shape", preset.shape()));
            if (!List.of("crosswise", "along", "centered", "leading_edge", "radial").contains(shape)) {
                throw new IllegalArgumentException("Unknown gradient shape '" + shape + "'");
            }
            int highlight = optionalColor(effect, "highlight_color");
            int shadow = optionalColor(effect, "shadow_color");
            effects.add(new BarEffect(type, strength, 1f, 3.6f, 1f,
                    -1, shape, highlight, shadow));
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
            throw new IllegalArgumentException("Expected #RRGGBB for resource effect " + key + ", got '" + raw + "'");
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
