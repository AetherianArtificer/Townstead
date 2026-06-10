package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.habitus.sound.SoundSpec;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Replaces one of the bearer's intrinsic sounds (Apugli's {@code custom_hurt_sound} /
 * {@code custom_death_sound} / {@code custom_footstep}). {@code slot} is {@code hurt},
 * {@code death} or {@code step}; {@code sound} is a {@link SoundSpec} (single, with
 * volume/pitch, or a weighted list). Resolved server-side by the sound mixins, so it
 * syncs to every client. (For sounds played as the <em>effect</em> of a power, use the
 * {@code play_sound} action instead.)
 *
 * <p>JSON: {@code { "type":"townstead_origins:custom_sound", "slot":"hurt",
 * "sound":"minecraft:entity.cat.hiss" }}</p>
 */
public final class CustomSoundGeneType implements GeneType {

    public static final String KEY = "townstead_origins:custom_sound";

    public enum Slot {
        HURT("hurt"), DEATH("death"), STEP("step");

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

    public record Instance(Slot slot, SoundSpec sound) implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        Slot slot = Slot.byKey(GsonHelper.getAsString(json, "slot", ""));
        if (slot == null) return null;
        SoundSpec sound = SoundSpec.parse(json.get("sound"));
        return sound == null ? null : new Instance(slot, sound);
    }
}
