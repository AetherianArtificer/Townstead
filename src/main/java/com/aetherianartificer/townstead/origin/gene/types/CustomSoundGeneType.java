package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.pheno.sound.SoundSpec;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Replaces one of the bearer's intrinsic sounds (Apugli's {@code custom_hurt_sound} /
 * {@code custom_death_sound} / {@code custom_footstep}). {@code slot} is {@code hurt},
 * {@code death} or {@code step}; {@code sound} is a {@link SoundSpec} (single, with
 * volume/pitch, or a weighted list). Resolved server-side by the sound mixins, so it
 * syncs to every client. (For sounds played as the <em>effect</em> of a power, use the
 * {@code play_sound} action instead.)
 *
 * <p>JSON: {@code { "type":"pheno:custom_sound", "slot":"hurt",
 * "sound":"minecraft:entity.cat.hiss" }}</p>
 */
public final class CustomSoundGeneType implements GeneType {

    public static final String KEY = "pheno:custom_sound";

    public enum Slot {
        AMBIENT("ambient"), HURT("hurt"), DEATH("death"), STEP("step"),
        GREET("greet"), YES("yes"), NO("no"), SURPRISE("surprise"), CELEBRATE("celebrate");

        private final String key;

        Slot(String key) { this.key = key; }

        @Nullable
        static Slot byKey(String raw) {
            if (raw == null) return null;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (Slot slot : values()) if (slot.key.equals(needle)) return slot;
            return null;
        }
    }

    /** A voice: one or more slots mapped to their replacement sound. */
    public record Instance(Map<Slot, SoundSpec> sounds) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Map<Slot, SoundSpec> sounds = new EnumMap<>(Slot.class);
        if (json.has("sounds") && json.get("sounds").isJsonObject()) {
            // A full voice: { "sounds": { "ambient": "...", "hurt": "...", "death": "..." } }.
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("sounds").entrySet()) {
                Slot slot = Slot.byKey(e.getKey());
                SoundSpec sound = SoundSpec.parse(e.getValue());
                if (slot != null && sound != null) sounds.put(slot, sound);
            }
        } else {
            // A single slot: { "slot": "hurt", "sound": "..." }.
            Slot slot = Slot.byKey(GsonHelper.getAsString(json, "slot", ""));
            SoundSpec sound = SoundSpec.parse(json.get("sound"));
            if (slot != null && sound != null) sounds.put(slot, sound);
        }
        return sounds.isEmpty() ? null : new Instance(Map.copyOf(sounds));
    }
}
