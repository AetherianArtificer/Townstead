package com.aetherianartificer.townstead.block.haze;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * One kind of haze, read from {@code data/<ns>/haze/<name>.json}. Every kind shares the single
 * {@code townstead:haze} block; the kind rides in its block state, so packs add kinds without
 * registering anything.
 *
 * @param duration      ticks a freshly raised cell lasts before it clears
 * @param fogDistance   fog far plane, in blocks, for a camera inside a cloud; 0 for no fog
 * @param particle      accent particle a cloud adds to its tinted dust, or null
 * @param texture       block-atlas sprite for a layer or cross, or null for the tinted default
 * @param insideAction  pheno action run on a living entity standing in the cell, every
 *                      {@code insideInterval} ticks, when {@code insideCondition} passes.
 *                      Server-only: clients receive kinds without it.
 */
public record HazeKind(ResourceLocation id, Shape shape, int color, int duration, boolean conceals,
                       float fogDistance, @Nullable ResourceLocation particle, @Nullable ResourceLocation texture,
                       @Nullable Condition insideCondition, @Nullable Action insideAction, int insideInterval) {

    public enum Shape {
        /** Invisible cell drawn in particles; fogs the camera and may conceal. */
        CLOUD,
        /** A flat splat lying on solid ground. */
        LAYER,
        /** Two crossed planes standing on solid ground, like a bush. */
        CROSS,
        /** A full, collidable cube: a temporary wall. */
        SOLID;

        public boolean grounded() {
            return this == LAYER || this == CROSS;
        }
    }

    public float red() { return ((color >> 16) & 0xFF) / 255f; }
    public float green() { return ((color >> 8) & 0xFF) / 255f; }
    public float blue() { return (color & 0xFF) / 255f; }

    /** Ticks between density steps, so a cell thins four times over its duration. */
    public int stepTicks() {
        return Math.max(5, duration / HazeBlock.MAX_DENSITY);
    }

    static HazeKind parse(ResourceLocation id, JsonObject json) {
        Shape shape = Shape.valueOf(GsonHelper.getAsString(json, "shape", "cloud").toUpperCase(Locale.ROOT));
        String rawColor = GsonHelper.getAsString(json, "color", "#FFFFFF").replace("#", "");
        int color = Integer.parseInt(rawColor, 16) & 0xFFFFFF;
        int duration = Math.max(20, Math.min(6000, GsonHelper.getAsInt(json, "duration", 200)));
        boolean conceals = GsonHelper.getAsBoolean(json, "conceals", false);
        float fog = json.has("fog") ? GsonHelper.getAsFloat(json.getAsJsonObject("fog"), "distance", 0f) : 0f;
        ResourceLocation particle = optionalId(json, "particle");
        ResourceLocation texture = optionalId(json, "texture");

        Condition condition = null;
        Action action = null;
        int interval = 10;
        if (json.has("inside")) {
            JsonObject inside = GsonHelper.getAsJsonObject(json, "inside");
            if (inside.has("condition")) {
                condition = Conditions.parse(inside.get("condition"));
                if (condition == null) throw new IllegalArgumentException("invalid inside.condition");
            }
            action = Actions.parse(inside.get("action"));
            if (action == null) throw new IllegalArgumentException("inside needs a valid action");
            interval = Math.max(1, GsonHelper.getAsInt(inside, "interval", 10));
        }
        return new HazeKind(id, shape, color, duration, conceals, Math.max(0f, fog), particle, texture,
                condition, action, interval);
    }

    @Nullable
    private static ResourceLocation optionalId(JsonObject json, String field) {
        if (!json.has(field)) return null;
        ResourceLocation parsed = ResourceLocation.tryParse(GsonHelper.getAsString(json, field));
        if (parsed == null) throw new IllegalArgumentException("invalid " + field);
        return parsed;
    }
}
