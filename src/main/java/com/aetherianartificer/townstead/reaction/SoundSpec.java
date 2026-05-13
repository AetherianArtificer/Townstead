package com.aetherianartificer.townstead.reaction;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * Optional per-binding sound effect played alongside the animation.
 *
 * <p>Two addressing modes:</p>
 * <ul>
 *   <li>{@code id} — a direct {@code SoundEvent} {@link ResourceLocation}
 *       (e.g. {@code minecraft:block.wood.hit}).
 *   <li>{@code mca_voice} — a gendered MCA villager voice suffix (e.g.
 *       {@code "cry"}, {@code "yes"}, {@code "laugh"}). Resolved at fire
 *       time to {@code mca:villager.<gender>.<suffix>} using the
 *       villager's {@code getGenetics().getGender().binary().getDataName()}.
 * </ul>
 *
 * <p>If both are set, {@code mca_voice} wins on MCA villagers and
 * {@code id} is used as a fallback when the entity has no gender data.</p>
 */
public record SoundSpec(Optional<ResourceLocation> id, Optional<String> mcaVoice, float volume, float pitchMin,
                        float pitchMax) {
    public static Optional<SoundSpec> fromJson(JsonObject json) {
        if (json == null) return Optional.empty();
        Optional<ResourceLocation> id = Optional.empty();
        if (json.has("id")) {
            String rawId = GsonHelper.getAsString(json, "id");
            if (rawId != null && !rawId.isBlank()) {
                ResourceLocation parsed = ResourceLocation.tryParse(rawId);
                if (parsed != null) id = Optional.of(parsed);
            }
        }
        Optional<String> mcaVoice = Optional.empty();
        if (json.has("mca_voice")) {
            String raw = GsonHelper.getAsString(json, "mca_voice");
            if (raw != null && !raw.isBlank()) mcaVoice = Optional.of(raw.toLowerCase());
        }
        if (id.isEmpty() && mcaVoice.isEmpty()) return Optional.empty();
        float volume = GsonHelper.getAsFloat(json, "volume", 1.0F);
        float pitch = GsonHelper.getAsFloat(json, "pitch", 1.0F);
        float pitchMin = pitch;
        float pitchMax = pitch;
        if (json.has("pitch_range") && json.get("pitch_range").isJsonArray()) {
            var arr = json.getAsJsonArray("pitch_range");
            if (arr.size() == 2) {
                try {
                    pitchMin = arr.get(0).getAsFloat();
                    pitchMax = arr.get(1).getAsFloat();
                } catch (Exception ignored) {}
            }
        }
        if (pitchMax < pitchMin) {
            float tmp = pitchMin;
            pitchMin = pitchMax;
            pitchMax = tmp;
        }
        return Optional.of(new SoundSpec(id, mcaVoice, volume, pitchMin, pitchMax));
    }
}
