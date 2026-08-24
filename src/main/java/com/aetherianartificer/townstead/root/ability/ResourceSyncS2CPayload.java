package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Server to owning player: gameplay state plus resolved presentation for resource meters. */
//? if neoforge {
public record ResourceSyncS2CPayload(List<Bar> bars) implements CustomPacketPayload {
//?} else {
/*public record ResourceSyncS2CPayload(java.util.List<ResourceSyncS2CPayload.Bar> bars) {
*///?}

    public record Effect(String type, float strength, float speed, float interval,
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
        public Effect(String type, float strength, float speed, float interval,
                      float frequency, int color,
                      String gradientShape, int highlightColor, int shadowColor) {
            this(type, strength, speed, interval, frequency, color,
                    gradientShape, highlightColor, shadowColor,
                    12, 0.18f, 0.92f, 0.65f, 0.20f,
                    5, 0.78f, 0.55f,
                    6, 2, 0.35f,
                    8, 0.45f, 0.65f, 0.80f);
        }

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect(String type, float strength, float speed, float interval,
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

        public Effect {
            type = type == null || type.isBlank() ? "townstead:none" : type;
            strength = Math.max(0f, Math.min(1f, strength));
            speed = Math.max(0.05f, Math.min(4f, speed));
            interval = Math.max(0.5f, Math.min(30f, interval));
            frequency = Math.max(0.5f, Math.min(8f, frequency));
            color = color < 0 ? -1 : color & 0xFFFFFF;
            gradientShape = gradientShape == null || gradientShape.isBlank()
                    ? "crosswise" : gradientShape;
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
            flamePlacement = flamePlacement == null || flamePlacement.isBlank()
                    ? "base" : flamePlacement.toLowerCase(Locale.ROOT);
            if (!"base".equals(flamePlacement) && !"surface".equals(flamePlacement)) {
                flamePlacement = "base";
            }
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
            runeMode = runeMode == null || runeMode.isBlank()
                    ? "scroll" : runeMode.toLowerCase(Locale.ROOT);
            if (!"scroll".equals(runeMode) && !"blink".equals(runeMode)) runeMode = "scroll";
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

    public record Reaction(String type, float strength, float duration, float speed,
                           int color, float threshold, String mode, float continuing) {
        public Reaction {
            type = type == null || type.isBlank() ? "townstead:gain_flash" : type;
            strength = Math.max(0f, Math.min(1f, strength));
            duration = Math.max(0.10f, Math.min(5f, duration));
            speed = Math.max(0.05f, Math.min(4f, speed));
            color = color < 0 ? -1 : color & 0xFFFFFF;
            threshold = Math.max(0f, Math.min(1f, threshold));
            mode = mode == null || mode.isBlank() ? "flash" : mode;
            continuing = Math.max(0f, Math.min(1f, continuing));
        }
    }

    public record Bar(String resourceId, int value, int min, int max, int restingValue,
                      int color,
                      String shape, String fillMode, List<Effect> effects, List<Reaction> reactions,
                      boolean abilityReady, int regenerationSequence,
                      String frameId, String colorThemeId,
                      String anchor, String pipStyle, int segments, int priority,
                      int backgroundColor, int framePrimaryColor, int frameSecondaryColor, int frameThickness,
                      String frameTexture, int frameSpriteRow) {
        public Bar {
            effects = effects == null ? List.of() : List.copyOf(effects);
            reactions = reactions == null ? List.of() : List.copyOf(reactions);
            anchor = anchor == null || anchor.isBlank() ? "TOP_LEFT" : anchor;
            pipStyle = pipStyle == null || pipStyle.isBlank() ? "DOTS" : pipStyle;
            frameTexture = frameTexture == null ? "" : frameTexture;
        }
    }

    //? if neoforge {
    public static final Type<ResourceSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, ResourceSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ResourceSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "resource_sync_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "resource_sync_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(bars.size());
        for (Bar bar : bars) {
            buf.writeUtf(bar.resourceId());
            buf.writeVarInt(bar.value());
            buf.writeVarInt(bar.min());
            buf.writeVarInt(bar.max());
            buf.writeVarInt(bar.restingValue());
            buf.writeInt(bar.color());
            buf.writeUtf(bar.shape());
            buf.writeUtf(bar.fillMode());
            buf.writeVarInt(bar.effects().size());
            for (Effect effect : bar.effects()) {
                buf.writeUtf(effect.type());
                buf.writeFloat(effect.strength());
                buf.writeFloat(effect.speed());
                buf.writeFloat(effect.interval());
                buf.writeFloat(effect.frequency());
                buf.writeInt(effect.color());
                buf.writeUtf(effect.gradientShape());
                buf.writeInt(effect.highlightColor());
                buf.writeInt(effect.shadowColor());
                buf.writeVarInt(effect.surfacePoints());
                buf.writeFloat(effect.tension());
                buf.writeFloat(effect.damping());
                buf.writeFloat(effect.splash());
                buf.writeFloat(effect.movementInfluence());
                buf.writeVarInt(effect.lobeCount());
                buf.writeFloat(effect.viscosity());
                buf.writeFloat(effect.stringiness());
                buf.writeVarInt(effect.bubbleCount());
                buf.writeVarInt(effect.bubbleSize());
                buf.writeFloat(effect.bubbleWobble());
                buf.writeVarInt(effect.emberCount());
                buf.writeFloat(effect.emberDrift());
                buf.writeFloat(effect.emberFlicker());
                buf.writeFloat(effect.emberEscape());
                buf.writeVarInt(effect.flameCount());
                buf.writeVarInt(effect.flameHeight());
                buf.writeFloat(effect.flameFlicker());
                buf.writeUtf(effect.flamePlacement());
                buf.writeVarInt(effect.steamCount());
                buf.writeVarInt(effect.steamSize());
                buf.writeFloat(effect.steamDrift());
                buf.writeVarInt(effect.electricCount());
                buf.writeFloat(effect.electricBranching());
                buf.writeFloat(effect.electricReach());
                buf.writeVarInt(effect.wispCount());
                buf.writeVarInt(effect.wispTrail());
                buf.writeFloat(effect.wispWander());
                buf.writeVarInt(effect.sparkleCount());
                buf.writeVarInt(effect.sparkleSize());
                buf.writeFloat(effect.sparkleTwinkle());
                buf.writeVarInt(effect.crystalCount());
                buf.writeVarInt(effect.crystalDepth());
                buf.writeFloat(effect.crystalGlint());
                buf.writeUtf(effect.runeMode());
                buf.writeVarInt(effect.runeSpacing());
                buf.writeUtf(effect.runeTexture());
                buf.writeVarInt(effect.runeGlyphWidth());
                buf.writeVarInt(effect.runeGlyphHeight());
                buf.writeVarInt(effect.runeColumns());
                buf.writeVarInt(effect.runeRows());
                buf.writeFloat(effect.runeEscape());
                buf.writeVarInt(effect.corruptionCount());
                buf.writeVarInt(effect.corruptionSize());
                buf.writeVarInt(effect.voidCount());
                buf.writeFloat(effect.voidInstability());
                buf.writeFloat(effect.prismaticWidth());
                buf.writeVarInt(effect.sporeCount());
                buf.writeVarInt(effect.sporeSize());
                buf.writeFloat(effect.sporeDrift());
                buf.writeVarInt(effect.fallingCount());
                buf.writeVarInt(effect.fallingSize());
                buf.writeFloat(effect.fallingDrift());
                buf.writeUtf(effect.fallingTexture());
                buf.writeVarInt(effect.fallingMarkWidth());
                buf.writeVarInt(effect.fallingMarkHeight());
                buf.writeVarInt(effect.fallingColumns());
                buf.writeVarInt(effect.fallingRows());
            }
            buf.writeVarInt(bar.reactions().size());
            for (Reaction reaction : bar.reactions()) {
                buf.writeUtf(reaction.type());
                buf.writeFloat(reaction.strength());
                buf.writeFloat(reaction.duration());
                buf.writeFloat(reaction.speed());
                buf.writeInt(reaction.color());
                buf.writeFloat(reaction.threshold());
                buf.writeUtf(reaction.mode());
                buf.writeFloat(reaction.continuing());
            }
            buf.writeBoolean(bar.abilityReady());
            buf.writeVarInt(bar.regenerationSequence());
            buf.writeUtf(bar.frameId());
            buf.writeUtf(bar.colorThemeId());
            buf.writeUtf(bar.anchor());
            buf.writeUtf(bar.pipStyle());
            buf.writeVarInt(bar.segments());
            buf.writeInt(bar.priority());
            buf.writeInt(bar.backgroundColor());
            buf.writeInt(bar.framePrimaryColor());
            buf.writeInt(bar.frameSecondaryColor());
            buf.writeVarInt(bar.frameThickness());
            buf.writeUtf(bar.frameTexture());
            buf.writeVarInt(bar.frameSpriteRow() + 1);
        }
    }

    public static ResourceSyncS2CPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Bar> bars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String resourceId = buf.readUtf();
            int value = buf.readVarInt();
            int min = buf.readVarInt();
            int max = buf.readVarInt();
            int resting = buf.readVarInt();
            int color = buf.readInt();
            String shape = buf.readUtf();
            String fill = buf.readUtf();
            int effectCount = buf.readVarInt();
            List<Effect> effects = new ArrayList<>(effectCount);
            for (int effectIndex = 0; effectIndex < effectCount; effectIndex++) {
                effects.add(new Effect(buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readInt(),
                        buf.readUtf(), buf.readInt(), buf.readInt(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readUtf(), buf.readVarInt(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                        buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readUtf(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
            }
            int reactionCount = buf.readVarInt();
            List<Reaction> reactions = new ArrayList<>(reactionCount);
            for (int reactionIndex = 0; reactionIndex < reactionCount; reactionIndex++) {
                reactions.add(new Reaction(buf.readUtf(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readInt(), buf.readFloat(), buf.readUtf(),
                        buf.readFloat()));
            }
            boolean abilityReady = buf.readBoolean();
            int regenerationSequence = buf.readVarInt();
            String frame = buf.readUtf();
            String colorTheme = buf.readUtf();
            String anchor = buf.readUtf();
            String pipStyle = buf.readUtf();
            int segments = buf.readVarInt();
            int priority = buf.readInt();
            int background = buf.readInt();
            int framePrimaryColor = buf.readInt();
            int frameSecondaryColor = buf.readInt();
            int thickness = buf.readVarInt();
            String frameTexture = buf.readUtf();
            int frameSpriteRow = buf.readVarInt() - 1;
            bars.add(new Bar(resourceId, value, min, max, resting, color,
                    shape, fill, List.copyOf(effects), List.copyOf(reactions),
                    abilityReady, regenerationSequence, frame, colorTheme,
                    anchor, pipStyle,
                    segments, priority, background, framePrimaryColor, frameSecondaryColor, thickness,
                    frameTexture, frameSpriteRow));
        }
        return new ResourceSyncS2CPayload(List.copyOf(bars));
    }
}
