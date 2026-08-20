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
                            float emberEscape,
                            int flameCount, int flameHeight, float flameFlicker,
                            String flamePlacement,
                            int steamCount, int steamSize, float steamDrift,
                            int electricCount, float electricBranching, float electricReach,
                            int wispCount, int wispTrail, float wispWander,
                            int sparkleCount, int sparkleSize, float sparkleTwinkle,
                            int crystalCount, int crystalDepth, float crystalGlint,
                            String runeMode, int runeSpacing, String runeTexture,
                            int runeGlyphWidth, int runeGlyphHeight, int runeColumns, int runeRows,
                            float runeEscape,
                            int corruptionCount, int corruptionSize,
                            int voidCount, float voidInstability,
                            float prismaticWidth,
                            int sporeCount, int sporeSize, float sporeDrift,
                            int fallingCount, int fallingSize, float fallingDrift,
                            String fallingTexture, int fallingMarkWidth, int fallingMarkHeight,
                            int fallingColumns, int fallingRows) {
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

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    7, 7, 0.80f, "base");
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, "base");
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    6, 3, 0.45f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    5, 0.45f, 0.65f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    5, 3, 0.70f,
                    6, 3, 0.65f,
                    7, 3, 0.55f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    6, 3, 0.65f,
                    7, 3, 0.55f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander,
                         int sparkleCount, int sparkleSize, float sparkleTwinkle) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    sparkleCount, sparkleSize, sparkleTwinkle,
                    7, 3, 0.55f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander,
                         int sparkleCount, int sparkleSize, float sparkleTwinkle,
                         int crystalCount, int crystalDepth, float crystalGlint) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    sparkleCount, sparkleSize, sparkleTwinkle,
                    crystalCount, crystalDepth, crystalGlint,
                    "scroll", 3, "", 3, 5, 8, 2);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander,
                         int sparkleCount, int sparkleSize, float sparkleTwinkle,
                         int crystalCount, int crystalDepth, float crystalGlint,
                         String runeMode, int runeSpacing, String runeTexture,
                         int runeGlyphWidth, int runeGlyphHeight, int runeColumns, int runeRows) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    sparkleCount, sparkleSize, sparkleTwinkle,
                    crystalCount, crystalDepth, crystalGlint,
                    runeMode, runeSpacing, runeTexture, runeGlyphWidth, runeGlyphHeight,
                    runeColumns, runeRows, 0.55f);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander,
                         int sparkleCount, int sparkleSize, float sparkleTwinkle,
                         int crystalCount, int crystalDepth, float crystalGlint,
                         String runeMode, int runeSpacing, String runeTexture,
                         int runeGlyphWidth, int runeGlyphHeight, int runeColumns, int runeRows,
                         float runeEscape) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    sparkleCount, sparkleSize, sparkleTwinkle,
                    crystalCount, crystalDepth, crystalGlint,
                    runeMode, runeSpacing, runeTexture, runeGlyphWidth, runeGlyphHeight,
                    runeColumns, runeRows, runeEscape,
                    8, 2);
        }

        public BarEffect(ResourceLocation type, float strength, float speed, float interval,
                         float frequency, int color,
                         String gradientShape, int highlightColor, int shadowColor,
                         int surfacePoints, float tension, float damping,
                         float splash, float movementInfluence,
                         int lobeCount, float viscosity, float stringiness,
                         int bubbleCount, int bubbleSize, float bubbleWobble,
                         int emberCount, float emberDrift, float emberFlicker,
                         float emberEscape,
                         int flameCount, int flameHeight, float flameFlicker,
                         String flamePlacement,
                         int steamCount, int steamSize, float steamDrift,
                         int electricCount, float electricBranching, float electricReach,
                         int wispCount, int wispTrail, float wispWander,
                         int sparkleCount, int sparkleSize, float sparkleTwinkle,
                         int crystalCount, int crystalDepth, float crystalGlint,
                         String runeMode, int runeSpacing, String runeTexture,
                         int runeGlyphWidth, int runeGlyphHeight, int runeColumns, int runeRows,
                         float runeEscape,
                         int corruptionCount, int corruptionSize) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    surfacePoints, tension, damping, splash, movementInfluence,
                    lobeCount, viscosity, stringiness,
                    bubbleCount, bubbleSize, bubbleWobble,
                    emberCount, emberDrift, emberFlicker, emberEscape,
                    flameCount, flameHeight, flameFlicker, flamePlacement,
                    steamCount, steamSize, steamDrift,
                    electricCount, electricBranching, electricReach,
                    wispCount, wispTrail, wispWander,
                    sparkleCount, sparkleSize, sparkleTwinkle,
                    crystalCount, crystalDepth, crystalGlint,
                    runeMode, runeSpacing, runeTexture, runeGlyphWidth, runeGlyphHeight,
                    runeColumns, runeRows, runeEscape,
                    corruptionCount, corruptionSize,
                    6, 0.60f, 0.35f,
                    8, 2, 0.45f,
                    9, 2, 0.35f, "", 2, 2, 8, 2);
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
            flameCount = Math.max(2, Math.min(16, flameCount));
            flameHeight = Math.max(1, Math.min(10, flameHeight));
            flameFlicker = Math.max(0f, Math.min(1f, flameFlicker));
            flamePlacement = normalize(flamePlacement);
            if (!List.of("base", "surface").contains(flamePlacement)) flamePlacement = "base";
            steamCount = Math.max(1, Math.min(16, steamCount));
            steamSize = Math.max(1, Math.min(4, steamSize));
            steamDrift = Math.max(0f, Math.min(1f, steamDrift));
            electricCount = Math.max(1, Math.min(12, electricCount));
            electricBranching = Math.max(0f, Math.min(1f, electricBranching));
            electricReach = Math.max(0f, Math.min(1f, electricReach));
            wispCount = Math.max(1, Math.min(12, wispCount));
            wispTrail = Math.max(0, Math.min(6, wispTrail));
            wispWander = Math.max(0f, Math.min(1f, wispWander));
            sparkleCount = Math.max(1, Math.min(16, sparkleCount));
            sparkleSize = Math.max(1, Math.min(5, sparkleSize));
            sparkleTwinkle = Math.max(0f, Math.min(1f, sparkleTwinkle));
            crystalCount = Math.max(0, Math.min(3, crystalCount));
            crystalDepth = Math.max(1, Math.min(5, crystalDepth));
            crystalGlint = Math.max(0f, Math.min(1f, crystalGlint));
            runeMode = normalize(runeMode);
            if (!List.of("scroll", "blink").contains(runeMode)) runeMode = "scroll";
            runeSpacing = Math.max(1, Math.min(8, runeSpacing));
            runeTexture = runeTexture == null ? "" : runeTexture.trim();
            runeGlyphWidth = Math.max(1, Math.min(16, runeGlyphWidth));
            runeGlyphHeight = Math.max(1, Math.min(16, runeGlyphHeight));
            runeColumns = Math.max(1, Math.min(32, runeColumns));
            runeRows = Math.max(1, Math.min(32, runeRows));
            runeEscape = Math.max(0f, Math.min(1f, runeEscape));
            corruptionCount = Math.max(1, Math.min(20, corruptionCount));
            corruptionSize = Math.max(1, Math.min(4, corruptionSize));
            voidCount = Math.max(1, Math.min(16, voidCount));
            voidInstability = Math.max(0f, Math.min(1f, voidInstability));
            prismaticWidth = Math.max(0.08f, Math.min(0.80f, prismaticWidth));
            sporeCount = Math.max(1, Math.min(16, sporeCount));
            sporeSize = Math.max(1, Math.min(3, sporeSize));
            sporeDrift = Math.max(0f, Math.min(1f, sporeDrift));
            fallingCount = Math.max(1, Math.min(20, fallingCount));
            fallingSize = Math.max(1, Math.min(4, fallingSize));
            fallingDrift = Math.max(0f, Math.min(1f, fallingDrift));
            fallingTexture = fallingTexture == null ? "" : fallingTexture.trim();
            fallingMarkWidth = Math.max(1, Math.min(16, fallingMarkWidth));
            fallingMarkHeight = Math.max(1, Math.min(16, fallingMarkHeight));
            fallingColumns = Math.max(1, Math.min(32, fallingColumns));
            fallingRows = Math.max(1, Math.min(32, fallingRows));
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
                    && !"townstead:embers".equals(type.toString())
                    && !"townstead:flames".equals(type.toString())
                    && !"townstead:steam".equals(type.toString())
                    && !"townstead:electric".equals(type.toString())
                    && !"townstead:wisps".equals(type.toString())
                    && !"townstead:sparkle".equals(type.toString())
                    && !"townstead:crystalline".equals(type.toString())
                    && !"townstead:runes".equals(type.toString())
                    && !"townstead:corruption".equals(type.toString())
                    && !"townstead:void".equals(type.toString())
                    && !"townstead:prismatic".equals(type.toString())
                    && !"townstead:spores".equals(type.toString())
                    && !"townstead:falling_motes".equals(type.toString())) {
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

            if ("townstead:flames".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.80f),
                        GsonHelper.getAsFloat(effect, "speed", 1.10f),
                        3.6f, 1f,
                        optionalColor(effect, "hot_color"), "crosswise", -1,
                        optionalColor(effect, "cool_color"),
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        GsonHelper.getAsInt(effect, "density", 7),
                        GsonHelper.getAsInt(effect, "height", 7),
                        GsonHelper.getAsFloat(effect, "flicker", 0.80f),
                        GsonHelper.getAsString(effect, "placement", "base")));
                continue;
            }

            if ("townstead:steam".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.65f),
                        GsonHelper.getAsFloat(effect, "speed", 0.75f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        GsonHelper.getAsInt(effect, "density", 6),
                        GsonHelper.getAsInt(effect, "size", 3),
                        GsonHelper.getAsFloat(effect, "drift", 0.45f)));
                continue;
            }

            if ("townstead:electric".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.80f),
                        GsonHelper.getAsFloat(effect, "speed", 1.25f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        GsonHelper.getAsInt(effect, "density", 5),
                        GsonHelper.getAsFloat(effect, "branching", 0.45f),
                        GsonHelper.getAsFloat(effect, "reach", 0.65f)));
                continue;
            }

            if ("townstead:wisps".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.70f),
                        GsonHelper.getAsFloat(effect, "speed", 0.85f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        5, 0.45f, 0.65f,
                        GsonHelper.getAsInt(effect, "density", 5),
                        GsonHelper.getAsInt(effect, "trail", 3),
                        GsonHelper.getAsFloat(effect, "wander", 0.70f)));
                continue;
            }

            if ("townstead:sparkle".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.75f),
                        GsonHelper.getAsFloat(effect, "speed", 1f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        5, 0.45f, 0.65f,
                        5, 3, 0.70f,
                        GsonHelper.getAsInt(effect, "density", 6),
                        GsonHelper.getAsInt(effect, "size", 3),
                        GsonHelper.getAsFloat(effect, "twinkle", 0.65f)));
                continue;
            }

            if ("townstead:crystalline".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.65f),
                        GsonHelper.getAsFloat(effect, "speed", 0.65f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        5, 0.45f, 0.65f,
                        5, 3, 0.70f,
                        6, 3, 0.65f,
                        GsonHelper.getAsInt(effect, "density", 2),
                        GsonHelper.getAsInt(effect, "depth", 2),
                        GsonHelper.getAsFloat(effect, "glint", 0.55f)));
                continue;
            }

            if ("townstead:runes".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.70f),
                        GsonHelper.getAsFloat(effect, "speed", 0.75f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        5, 0.45f, 0.65f,
                        5, 3, 0.70f,
                        6, 3, 0.65f,
                        7, 3, 0.55f,
                        GsonHelper.getAsString(effect, "mode", "scroll"),
                        GsonHelper.getAsInt(effect, "spacing", 3),
                        GsonHelper.getAsString(effect, "texture", ""),
                        GsonHelper.getAsInt(effect, "glyph_width", 3),
                        GsonHelper.getAsInt(effect, "glyph_height", 5),
                        GsonHelper.getAsInt(effect, "columns", 8),
                        GsonHelper.getAsInt(effect, "rows", 2),
                        GsonHelper.getAsFloat(effect, "escape", 0.55f)));
                continue;
            }

            if ("townstead:corruption".equals(type.toString())) {
                effects.add(new BarEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.75f),
                        GsonHelper.getAsFloat(effect, "speed", 0.80f),
                        3.6f, 1f,
                        optionalColor(effect, "color"), "crosswise", -1, -1,
                        12, 0.18f, 0.92f, 0.65f, 0.20f,
                        5, 0.78f, 0.55f,
                        6, 2, 0.35f,
                        8, 0.45f, 0.65f, 0.80f,
                        7, 7, 0.80f, "base",
                        6, 3, 0.45f,
                        5, 0.45f, 0.65f,
                        5, 3, 0.70f,
                        6, 3, 0.65f,
                        7, 3, 0.55f,
                        "scroll", 3, "", 3, 5, 8, 2, 0.55f,
                        GsonHelper.getAsInt(effect, "density", 8),
                        GsonHelper.getAsInt(effect, "size", 2)));
                continue;
            }

            if ("townstead:void".equals(type.toString())) {
                effects.add(extendedEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.70f),
                        GsonHelper.getAsFloat(effect, "speed", 0.75f),
                        -1,
                        GsonHelper.getAsInt(effect, "density", 6),
                        GsonHelper.getAsFloat(effect, "instability", 0.60f),
                        0.35f, 7, 2, 0.45f,
                        9, 2, 0.35f, "", 2, 2, 8, 2));
                continue;
            }

            if ("townstead:prismatic".equals(type.toString())) {
                effects.add(extendedEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.65f),
                        GsonHelper.getAsFloat(effect, "speed", 0.70f),
                        -1, 6, 0.60f,
                        GsonHelper.getAsFloat(effect, "band_width", 0.35f),
                        7, 2, 0.45f,
                        9, 2, 0.35f, "", 2, 2, 8, 2));
                continue;
            }

            if ("townstead:spores".equals(type.toString())) {
                effects.add(extendedEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.55f),
                        GsonHelper.getAsFloat(effect, "speed", 0.65f),
                        optionalColor(effect, "color"), 6, 0.60f, 0.35f,
                        GsonHelper.getAsInt(effect, "density", 8),
                        GsonHelper.getAsInt(effect, "size", 2),
                        GsonHelper.getAsFloat(effect, "drift", 0.45f),
                        9, 2, 0.35f, "", 2, 2, 8, 2));
                continue;
            }

            if ("townstead:falling_motes".equals(type.toString())) {
                effects.add(extendedEffect(type,
                        GsonHelper.getAsFloat(effect, "strength", 0.65f),
                        GsonHelper.getAsFloat(effect, "speed", 0.75f),
                        optionalColor(effect, "color"), 6, 0.60f, 0.35f,
                        7, 2, 0.45f,
                        GsonHelper.getAsInt(effect, "density", 9),
                        GsonHelper.getAsInt(effect, "size", 2),
                        GsonHelper.getAsFloat(effect, "drift", 0.35f),
                        GsonHelper.getAsString(effect, "texture", ""),
                        GsonHelper.getAsInt(effect, "mark_width", 2),
                        GsonHelper.getAsInt(effect, "mark_height", 2),
                        GsonHelper.getAsInt(effect, "columns", 8),
                        GsonHelper.getAsInt(effect, "rows", 2)));
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

    private static BarEffect extendedEffect(ResourceLocation type, float strength, float speed,
                                             int color,
                                             int voidCount, float voidInstability,
                                             float prismaticWidth,
                                             int sporeCount, int sporeSize, float sporeDrift,
                                             int fallingCount, int fallingSize, float fallingDrift,
                                             String fallingTexture, int fallingMarkWidth,
                                             int fallingMarkHeight, int fallingColumns,
                                             int fallingRows) {
        return new BarEffect(type, strength, speed, 3.6f, 1f,
                color, "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.80f,
                7, 7, 0.80f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                5, 3, 0.70f,
                6, 3, 0.65f,
                7, 3, 0.55f,
                "scroll", 3, "", 3, 5, 8, 2, 0.55f,
                8, 2,
                voidCount, voidInstability, prismaticWidth,
                sporeCount, sporeSize, sporeDrift,
                fallingCount, fallingSize, fallingDrift,
                fallingTexture, fallingMarkWidth, fallingMarkHeight,
                fallingColumns, fallingRows);
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
