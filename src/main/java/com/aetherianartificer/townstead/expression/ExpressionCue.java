package com.aetherianartificer.townstead.expression;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/** A server-authored, client-rendered thought or reaction above an entity. */
public record ExpressionCue(ResourceLocation id, Kind kind, String content, String fallbackParticle,
                            int durationTicks, int cooldownTicks, float rise, float drift, float scale, int color,
                            Audience audience, double maxDistance, IconBurst iconBurst,
                            TextBurst textBurst, ParticleBurst particleBurst) {
    public enum Kind { ICON, TEXT }
    public enum Audience { TRACKING, NEARBY, TARGET }

    /** Client-rendered copies of a custom expression texture, moving like a small particle cloud. */
    public record IconBurst(int count, float spread, float verticalSpread, int staggerTicks,
                            float scaleVariance) {
        public static final IconBurst SINGLE = new IconBurst(1, 0f, 0f, 0, 0f);

        public IconBurst {
            count = Math.max(1, Math.min(32, count));
            spread = clamp(spread, 0f, 4f);
            verticalSpread = clamp(verticalSpread, 0f, 4f);
            staggerTicks = Math.max(0, Math.min(40, staggerTicks));
            scaleVariance = clamp(scaleVariance, 0f, 0.9f);
        }
    }

    /** A localized sequence of short phrases, each with its own lifetime and motion. */
    public record TextBurst(List<String> translations, float spread, float verticalSpread,
                            int staggerTicks, float scaleVariance) {
        public TextBurst {
            ArrayList<String> clean = new ArrayList<>();
            if (translations != null) {
                for (String translation : translations) {
                    String key = translation == null ? "" : translation.trim();
                    if (key.isEmpty()) continue;
                    if (key.length() > 256) throw new IllegalArgumentException("text translation key is too long");
                    clean.add(key);
                    if (clean.size() == 12) break;
                }
            }
            translations = List.copyOf(clean);
            spread = clamp(spread, 0f, 4f);
            verticalSpread = clamp(verticalSpread, 0f, 4f);
            staggerTicks = Math.max(0, Math.min(80, staggerTicks));
            scaleVariance = clamp(scaleVariance, 0f, 0.9f);
        }

        public static TextBurst single(String translation) {
            return new TextBurst(List.of(translation), 0f, 0f, 0, 0f);
        }
    }

    /** Optional vanilla particle accompaniment, resolved and emitted by the server. */
    public record ParticleBurst(String type, int count, float spread, float verticalSpread, float speed) {
        public static final ParticleBurst NONE = new ParticleBurst("", 0, 0f, 0f, 0f);

        public ParticleBurst {
            type = type == null ? "" : type.trim();
            count = Math.max(0, Math.min(64, count));
            spread = clamp(spread, 0f, 4f);
            verticalSpread = clamp(verticalSpread, 0f, 4f);
            speed = clamp(speed, 0f, 2f);
        }
    }

    public ExpressionCue {
        if (id == null) throw new IllegalArgumentException("expression id is required");
        if (kind == null) kind = Kind.TEXT;
        content = content == null ? "" : content.trim();
        fallbackParticle = fallbackParticle == null ? "" : fallbackParticle.trim();
        durationTicks = Math.max(10, Math.min(400, durationTicks));
        cooldownTicks = Math.max(10, Math.min(2400, cooldownTicks));
        rise = clamp(rise, -2f, 4f);
        drift = clamp(drift, -2f, 2f);
        scale = clamp(scale, 0.25f, 4f);
        audience = audience == null ? Audience.TRACKING : audience;
        maxDistance = Math.max(4d, Math.min(128d, maxDistance));
        iconBurst = iconBurst == null ? IconBurst.SINGLE : iconBurst;
        particleBurst = particleBurst == null ? ParticleBurst.NONE : particleBurst;
        if (content.isEmpty()) throw new IllegalArgumentException("expression content is required");
        if (kind == Kind.ICON && DataPackLang.parseId(content) == null) {
            throw new IllegalArgumentException("icon content must be a texture resource id");
        }
        if (kind == Kind.TEXT && content.length() > 256) {
            throw new IllegalArgumentException("text translation key is too long");
        }
        textBurst = textBurst == null || textBurst.translations().isEmpty()
                ? TextBurst.single(content) : textBurst;
    }

    public static ExpressionCue parse(ResourceLocation id, JsonObject json) {
        String kindName = GsonHelper.getAsString(json, "kind", "text");
        Kind kind;
        try { kind = Kind.valueOf(kindName.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("kind must be icon or text"); }
        String content = kind == Kind.ICON
                ? GsonHelper.getAsString(json, "texture", "")
                : GsonHelper.getAsString(json, "translation", "");
        String audienceName = GsonHelper.getAsString(json, "audience", "tracking");
        Audience audience;
        try { audience = Audience.valueOf(audienceName.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("unknown audience '" + audienceName + "'"); }
        String fallbackParticle = GsonHelper.getAsString(json, "fallback_particle", "");
        JsonObject iconJson = object(json, "icon_burst");
        IconBurst iconBurst = iconJson == null ? IconBurst.SINGLE : new IconBurst(
                GsonHelper.getAsInt(iconJson, "count", 1),
                GsonHelper.getAsFloat(iconJson, "spread", 0f),
                GsonHelper.getAsFloat(iconJson, "vertical_spread", 0f),
                GsonHelper.getAsInt(iconJson, "stagger_ticks", 0),
                GsonHelper.getAsFloat(iconJson, "scale_variance", 0f));
        JsonObject textJson = object(json, "text_burst");
        List<String> translations = translations(textJson);
        if (kind == Kind.TEXT && content.isBlank() && !translations.isEmpty()) content = translations.get(0);
        if (kind == Kind.TEXT && translations.isEmpty() && !content.isBlank()) translations = List.of(content);
        TextBurst textBurst = textJson == null
                ? new TextBurst(translations, 0f, 0f, 0, 0f)
                : new TextBurst(translations,
                GsonHelper.getAsFloat(textJson, "spread", 0f),
                GsonHelper.getAsFloat(textJson, "vertical_spread", 0f),
                GsonHelper.getAsInt(textJson, "stagger_ticks", 10),
                GsonHelper.getAsFloat(textJson, "scale_variance", 0f));
        JsonObject particleJson = object(json, "particle_burst");
        ParticleBurst particleBurst = particleJson == null
                ? (fallbackParticle.isBlank() ? ParticleBurst.NONE
                : new ParticleBurst(fallbackParticle, 2, 0.15f, 0.1f, 0.01f))
                : new ParticleBurst(
                GsonHelper.getAsString(particleJson, "type", fallbackParticle),
                GsonHelper.getAsInt(particleJson, "count", 6),
                GsonHelper.getAsFloat(particleJson, "spread", 0.25f),
                GsonHelper.getAsFloat(particleJson, "vertical_spread", 0.15f),
                GsonHelper.getAsFloat(particleJson, "speed", 0.01f));
        return new ExpressionCue(id, kind, content, fallbackParticle,
                GsonHelper.getAsInt(json, "duration_ticks", 50),
                GsonHelper.getAsInt(json, "cooldown_ticks", 80),
                GsonHelper.getAsFloat(json, "rise", 0.35f),
                GsonHelper.getAsFloat(json, "drift", 0f),
                GsonHelper.getAsFloat(json, "scale", 1f),
                parseColor(GsonHelper.getAsString(json, "color", "#FFFFFFFF")),
                audience, GsonHelper.getAsDouble(json, "max_distance", 48d), iconBurst, textBurst,
                particleBurst);
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static List<String> translations(JsonObject textBurst) {
        if (textBurst == null || !textBurst.has("translations")
                || !textBurst.get("translations").isJsonArray()) return List.of();
        ArrayList<String> values = new ArrayList<>();
        for (var element : textBurst.getAsJsonArray("translations")) {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    static int parseColor(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.startsWith("#")) value = value.substring(1);
        if (value.length() == 6) value = "FF" + value;
        if (value.length() != 8) throw new IllegalArgumentException("color must be #RRGGBB or #AARRGGBB");
        try { return (int) Long.parseLong(value, 16); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("invalid color"); }
    }

    private static float clamp(float value, float min, float max) {
        if (!Float.isFinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }
}
