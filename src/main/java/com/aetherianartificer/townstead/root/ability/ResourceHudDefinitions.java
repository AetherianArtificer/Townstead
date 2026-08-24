package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.gene.types.ResourceDisplay;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reloadable, server-authored visual presets for resource frame geometry and frame colour themes. Resolved
 * values ride with the resource snapshot, so a server datapack can style a HUD without requiring
 * matching Java code on the client. New texture assets still require a resource pack.
 */
public final class ResourceHudDefinitions {

    /** A two-colour frame palette. Bar fill, frame geometry and fill mode are independent. */
    public record ColorTheme(int framePrimaryColor, int frameSecondaryColor) {}

    /** Resource-pack artwork for one canonical HUD shape. Every supplied PNG is the shape's exact GUI size. */
    public record FrameArt(ResourceLocation baseTexture,
                           ResourceLocation primaryTexture,
                           ResourceLocation secondaryTexture) {
        public boolean present() {
            return baseTexture != null || primaryTexture != null || secondaryTexture != null;
        }
    }

    public record Frame(int backgroundColor, int thickness,
                        ResourceLocation spriteTexture, int spriteRow,
                        FrameArt horizontalArt, FrameArt verticalArt, FrameArt squircleArt) {
        public Frame(int backgroundColor, int thickness) {
            this(backgroundColor, thickness, null, -1, null, null, null);
        }

        public Frame(int backgroundColor, int thickness,
                     ResourceLocation spriteTexture, int spriteRow) {
            this(backgroundColor, thickness, spriteTexture, spriteRow, null, null, null);
        }

        public boolean hasSprite() {
            return spriteTexture != null && spriteRow >= 0;
        }

        public FrameArt art(ResourceDisplay.Shape shape) {
            return switch (shape) {
                case VERTICAL -> verticalArt;
                case SQUIRCLE -> squircleArt;
                default -> horizontalArt;
            };
        }
    }

    private static final ResourceLocation PLAIN = id("townstead:plain");
    private static final ResourceLocation ARCANE = id("townstead:arcane");
    private static final Map<ResourceLocation, ColorTheme> DEFAULT_COLOR_THEMES = Map.of(
            ARCANE, new ColorTheme(0x3FA0FF, 0xC8F3FF),
            id("townstead:biological"), new ColorTheme(0xB52E3A, 0xFF7B83),
            id("townstead:spiritual"), new ColorTheme(0x77D7C8, 0xBFFFEF),
            id("townstead:mechanical"), new ColorTheme(0xE8A33C, 0xFFE3A0),
            id("townstead:otherworldly"), new ColorTheme(0xB05CDE, 0x63E7FF),
            PLAIN, new ColorTheme(0x3FA0FF, 0xACD8FF));
    private static final Map<ResourceLocation, Frame> DEFAULT_FRAMES = Map.ofEntries(
            Map.entry(PLAIN, artFrame(0xFF202020, 1)),
            Map.entry(id("townstead:corner_brackets"), artFrame(0xFF171721, 2)),
            Map.entry(id("townstead:runed_stone"), artFrame(0xFF17191F, 2)),
            Map.entry(id("townstead:forged_iron"), artFrame(0xFF181818, 2)),
            Map.entry(id("townstead:brass_apparatus"), artFrame(0xFF1B1A18, 2)),
            Map.entry(id("townstead:ropebound"), artFrame(0xFF1B1712, 2)),
            Map.entry(id("townstead:thornwood"), artFrame(0xFF17130F, 2)),
            Map.entry(id("townstead:bone_reliquary"), artFrame(0xFF171715, 2)),
            Map.entry(id("townstead:crystal_growth"), artFrame(0xFF14131B, 2)),
            Map.entry(id("townstead:spider_silk"), artFrame(0xFF11151A, 1)),
            Map.entry(id("townstead:haunted_fracture"), artFrame(0xFF101519, 2)),
            Map.entry(id("townstead:celestial_filigree"), artFrame(0xFF131622, 2)),
            Map.entry(id("townstead:beveled"), artFrame(0xFF171721, 2)),
            Map.entry(id("townstead:organic"), artFrame(0xFF251317, 2)),
            Map.entry(id("townstead:gauge"), artFrame(0xFF1B1A18, 2)),
            Map.entry(id("townstead:spirit_trough"), artFrame(0xFF14131B, 2)));

    private static volatile Map<ResourceLocation, ColorTheme> COLOR_THEMES = DEFAULT_COLOR_THEMES;
    private static volatile Map<ResourceLocation, Frame> FRAMES = DEFAULT_FRAMES;

    private ResourceHudDefinitions() {}

    public static ColorTheme colorTheme(ResourceLocation id) {
        return COLOR_THEMES.getOrDefault(id, DEFAULT_COLOR_THEMES.get(ARCANE));
    }

    public static Frame frame(ResourceLocation id) {
        return FRAMES.getOrDefault(id, DEFAULT_FRAMES.get(PLAIN));
    }

    private static void replaceColorThemes(Map<ResourceLocation, ColorTheme> parsed) {
        Map<ResourceLocation, ColorTheme> all = new LinkedHashMap<>(DEFAULT_COLOR_THEMES);
        all.putAll(parsed);
        COLOR_THEMES = Map.copyOf(all);
    }

    private static void replaceFrames(Map<ResourceLocation, Frame> parsed) {
        Map<ResourceLocation, Frame> all = new LinkedHashMap<>(DEFAULT_FRAMES);
        all.putAll(parsed);
        FRAMES = Map.copyOf(all);
    }

    private static ResourceLocation id(String raw) {
        return DataPackLang.parseId(raw);
    }

    private static Frame artFrame(int background, int thickness) {
        // Built-in frames deliberately use the aligned procedural fallback until final,
        // human-authored sprites provide explicit per-shape bounds and content insets.
        return new Frame(background, thickness);
    }

    private static int color(JsonObject obj, String key, int fallback) {
        if (!obj.has(key)) return fallback;
        String raw = GsonHelper.getAsString(obj, key, "");
        String hex = raw.startsWith("#") ? raw.substring(1) : raw;
        try {
            return Integer.parseUnsignedInt(hex, 16) | 0xFF000000;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static ColorTheme parseColorTheme(JsonObject obj) {
        String schema = GsonHelper.getAsString(obj, "schema", "townstead:resource_color_theme/v1");
        if (!"townstead:resource_color_theme/v1".equals(schema)) {
            throw new IllegalArgumentException("Expected resource color theme schema v1, got '" + schema + "'");
        }
        int primary = color(obj, "primary_color", 0xFF3FA0FF) & 0xFFFFFF;
        int secondary = color(obj, "secondary_color", 0xFF000000 | derivedSecondary(primary)) & 0xFFFFFF;
        return new ColorTheme(primary, secondary);
    }

    static Frame parseFrame(JsonObject obj) {
        String schema = GsonHelper.getAsString(obj, "schema", "townstead:resource_frame/v1");
        if (!"townstead:resource_frame/v1".equals(schema)
                && !"townstead:resource_frame/v2".equals(schema)
                && !"townstead:resource_frame/v3".equals(schema)) {
            throw new IllegalArgumentException(
                    "Expected resource frame schema v1, v2 or v3, got '" + schema + "'");
        }
        int background = color(obj, "background_color", 0xFF202020);
        int thickness = Math.max(1, Math.min(4, GsonHelper.getAsInt(obj, "thickness", 1)));
        ResourceLocation texture = null;
        int row = -1;
        if (obj.has("sprite") && obj.get("sprite").isJsonObject()) {
            JsonObject sprite = obj.getAsJsonObject("sprite");
            texture = requiredTexture(sprite, "texture");
            row = Math.max(0, Math.min(255, GsonHelper.getAsInt(sprite, "row", 0)));
        }

        FrameArt horizontal = null;
        FrameArt vertical = null;
        FrameArt squircle = null;
        if (obj.has("art") && obj.get("art").isJsonObject()) {
            if (!"townstead:resource_frame/v3".equals(schema)) {
                throw new IllegalArgumentException("Per-shape frame art requires resource frame schema v3");
            }
            JsonObject art = obj.getAsJsonObject("art");
            horizontal = parseFrameArt(art, "horizontal");
            vertical = parseFrameArt(art, "vertical");
            squircle = parseFrameArt(art, "squircle");
        }
        return new Frame(background, thickness, texture, row, horizontal, vertical, squircle);
    }

    private static FrameArt parseFrameArt(JsonObject art, String shape) {
        if (!art.has(shape)) return null;
        if (!art.get(shape).isJsonObject()) {
            throw new IllegalArgumentException("Frame art '" + shape + "' must be an object");
        }
        JsonObject value = art.getAsJsonObject(shape);
        FrameArt parsed = new FrameArt(optionalTexture(value, "base_texture"),
                optionalTexture(value, "primary_texture"),
                optionalTexture(value, "secondary_texture"));
        if (!parsed.present()) {
            throw new IllegalArgumentException("Frame art '" + shape + "' must supply at least one texture layer");
        }
        return parsed;
    }

    private static ResourceLocation optionalTexture(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        String raw = GsonHelper.getAsString(obj, key, "").trim();
        return raw.isEmpty() ? null : id(raw);
    }

    private static ResourceLocation requiredTexture(JsonObject obj, String key) {
        ResourceLocation texture = optionalTexture(obj, key);
        if (texture == null) throw new IllegalArgumentException("Frame sprite requires '" + key + "'");
        return texture;
    }

    private static int derivedSecondary(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r += Math.round((255 - r) * 0.45f);
        g += Math.round((255 - g) * 0.45f);
        b += Math.round((255 - b) * 0.45f);
        return (r << 16) | (g << 8) | b;
    }

    public static final class ColorThemeLoader extends SimpleJsonResourceReloadListener {
        private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ResourceColorThemes");
        private static final Gson GSON = new Gson();

        public ColorThemeLoader() {
            super(GSON, "resource_color_theme");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, ColorTheme> parsed = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    parsed.put(entry.getKey(), parseColorTheme(obj));
                } catch (Exception ex) {
                    LOGGER.warn("Failed to load resource color theme {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            replaceColorThemes(parsed);
            LOGGER.info("Loaded {} custom resource color themes", parsed.size());
        }
    }

    public static final class FrameLoader extends SimpleJsonResourceReloadListener {
        private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ResourceFrames");
        private static final Gson GSON = new Gson();

        public FrameLoader() {
            super(GSON, "resource_frame");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                             ProfilerFiller profiler) {
            Map<ResourceLocation, Frame> parsed = new LinkedHashMap<>();
            for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
                try {
                    JsonObject obj = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey().toString());
                    parsed.put(entry.getKey(), parseFrame(obj));
                } catch (Exception ex) {
                    LOGGER.warn("Failed to load resource frame {}: {}", entry.getKey(), ex.getMessage());
                }
            }
            replaceFrames(parsed);
            LOGGER.info("Loaded {} custom resource frames", parsed.size());
        }
    }
}
