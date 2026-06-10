package com.aetherianartificer.townstead.habitus.sound;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A reusable sound field for any effect that emits a sound (Apoli's
 * {@code sound_event_with_volume_and_pitch} and Apugli's {@code weighted_sound_event}
 * data types, unified). Parses one of:
 * <ul>
 *   <li>a bare id string ({@code "minecraft:entity.cat.purr"}),</li>
 *   <li>an object {@code { "sound", "volume", "pitch" }},</li>
 *   <li>an array of {@code { "sound", "volume", "pitch", "weight" }} for a weighted pick.</li>
 * </ul>
 * Lives in habitus so any source (genetics now, professions later) emits sounds the same way.
 */
public final class SoundSpec {

    public record Entry(SoundEvent sound, float volume, float pitch, int weight) {}

    private final List<Entry> entries;
    private final int totalWeight;

    private SoundSpec(List<Entry> entries) {
        this.entries = entries;
        int total = 0;
        for (Entry entry : entries) total += Math.max(1, entry.weight());
        this.totalWeight = total;
    }

    /** Parse a sound element (string, object, or weighted array). */
    @Nullable
    public static SoundSpec parse(@Nullable JsonElement element) {
        if (element == null) return null;
        List<Entry> entries = new ArrayList<>();
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                Entry entry = entry(child);
                if (entry != null) entries.add(entry);
            }
        } else {
            Entry entry = entry(element);
            if (entry != null) entries.add(entry);
        }
        return entries.isEmpty() ? null : new SoundSpec(entries);
    }

    /**
     * Read a sound spec from a containing object: a {@code "sounds"} array (weighted) takes
     * precedence, else the object's own {@code "sound"}/{@code "volume"}/{@code "pitch"}
     * fields form a single entry. Lets {@code play_sound} accept either shape.
     */
    @Nullable
    public static SoundSpec read(JsonObject obj) {
        if (obj.has("sounds")) return parse(obj.get("sounds"));
        if (obj.has("sound")) return parse(obj);
        return null;
    }

    @Nullable
    private static Entry entry(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return entry(DataPackLang.parseId(element.getAsString()), 1f, 1f, 1);
        }
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            return entry(DataPackLang.parseId(GsonHelper.getAsString(obj, "sound", "")),
                    GsonHelper.getAsFloat(obj, "volume", 1f),
                    GsonHelper.getAsFloat(obj, "pitch", 1f),
                    GsonHelper.getAsInt(obj, "weight", 1));
        }
        return null;
    }

    @Nullable
    private static Entry entry(@Nullable ResourceLocation id, float volume, float pitch, int weight) {
        if (id == null) return null;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
        return sound == null ? null : new Entry(sound, volume, pitch, weight);
    }

    /** Weighted pick (a single-entry spec always returns that entry). */
    public Entry pick(RandomSource random) {
        if (entries.size() == 1) return entries.get(0);
        int roll = random.nextInt(totalWeight);
        for (Entry entry : entries) {
            roll -= Math.max(1, entry.weight());
            if (roll < 0) return entry;
        }
        return entries.get(entries.size() - 1);
    }

    /** Pick a sound and play it at a position. The raw {@code SoundEvent} overload is uniform on both branches. */
    public void playAt(Level level, double x, double y, double z, SoundSource source, RandomSource random) {
        Entry entry = pick(random);
        level.playSound(null, x, y, z, entry.sound(), source, entry.volume(), entry.pitch());
    }
}
