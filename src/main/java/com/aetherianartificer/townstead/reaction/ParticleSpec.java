package com.aetherianartificer.townstead.reaction;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * Optional per-binding particle burst spawned at the villager when the
 * animation fires. Spread is the per-axis gaussian standard deviation
 * (typed as block units) and {@code yOffset} lifts the spawn origin.
 */
public record ParticleSpec(ResourceLocation id, int count, float spreadX, float spreadY, float spreadZ,
                           float yOffset) {
    public static Optional<ParticleSpec> fromJson(JsonObject json) {
        if (json == null) return Optional.empty();
        String rawId = GsonHelper.getAsString(json, "id", null);
        if (rawId == null || rawId.isBlank()) return Optional.empty();
        ResourceLocation parsed = ResourceLocation.tryParse(rawId);
        if (parsed == null) return Optional.empty();
        int count = Math.max(0, GsonHelper.getAsInt(json, "count", 1));
        float sx = 0.2F;
        float sy = 0.2F;
        float sz = 0.2F;
        if (json.has("spread") && json.get("spread").isJsonArray()) {
            JsonArray arr = json.getAsJsonArray("spread");
            if (arr.size() == 3) {
                try {
                    sx = arr.get(0).getAsFloat();
                    sy = arr.get(1).getAsFloat();
                    sz = arr.get(2).getAsFloat();
                } catch (Exception ignored) {}
            }
        }
        float yOffset = GsonHelper.getAsFloat(json, "y_offset", 1.0F);
        return Optional.of(new ParticleSpec(parsed, count, sx, sy, sz, yOffset));
    }
}
