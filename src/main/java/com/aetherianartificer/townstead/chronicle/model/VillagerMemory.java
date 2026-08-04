package com.aetherianartificer.townstead.chronicle.model;


import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A merged, decaying memory a villager holds. Derived from accounts (what they
 * believe), not from events directly, so misremembered details persist here.
 * Same {@code memoryKey} + {@code otherParty} merge on re-learn.
 */
public final class VillagerMemory {

    private final String memoryKey;
    private final @Nullable UUID otherParty;
    private long firstDay;
    private long lastDay;
    private int count;
    private float strength;
    private float valence;
    private final Map<String, String> params;

    public VillagerMemory(String memoryKey, @Nullable UUID otherParty, long day,
                          float strength, float valence, Map<String, String> params) {
        this.memoryKey = memoryKey;
        this.otherParty = otherParty;
        this.firstDay = day;
        this.lastDay = day;
        this.count = 1;
        this.strength = strength;
        this.valence = valence;
        this.params = params == null ? new HashMap<>() : new HashMap<>(params);
    }

    public String memoryKey() { return memoryKey; }
    public @Nullable UUID otherParty() { return otherParty; }
    public long firstDay() { return firstDay; }
    public long lastDay() { return lastDay; }
    public int count() { return count; }
    public float strength() { return strength; }
    public float valence() { return valence; }
    public Map<String, String> params() { return params; }

    public boolean matches(String key, @Nullable UUID other) {
        return memoryKey.equals(key)
                && (otherParty == null ? other == null : otherParty.equals(other));
    }

    public void reinforce(long day, float addedStrength, float newValence) {
        count++;
        lastDay = Math.max(lastDay, day);
        firstDay = Math.min(firstDay, day);
        strength += addedStrength;
        // Newest telling colors the feeling; average keeps old grudges sticky.
        valence = (valence + newValence) / 2f;
    }

    public void decay(float factor) {
        strength *= factor;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("key", memoryKey);
        if (otherParty != null) tag.putUUID("other", otherParty);
        tag.putLong("first", firstDay);
        tag.putLong("last", lastDay);
        tag.putInt("count", count);
        tag.putFloat("strength", strength);
        tag.putFloat("valence", valence);
        if (!params.isEmpty()) {
            CompoundTag p = new CompoundTag();
            for (Map.Entry<String, String> e : params.entrySet()) p.putString(e.getKey(), e.getValue());
            tag.put("params", p);
        }
        return tag;
    }

    public static VillagerMemory load(CompoundTag tag) {
        Map<String, String> params = new HashMap<>();
        if (tag.contains("params")) {
            CompoundTag p = tag.getCompound("params");
            for (String k : p.getAllKeys()) params.put(k, p.getString(k));
        }
        VillagerMemory memory = new VillagerMemory(
                tag.getString("key"),
                tag.hasUUID("other") ? tag.getUUID("other") : null,
                tag.getLong("first"),
                tag.getFloat("strength"),
                tag.getFloat("valence"),
                params);
        memory.lastDay = tag.getLong("last");
        memory.count = Math.max(1, tag.getInt("count"));
        return memory;
    }
}
