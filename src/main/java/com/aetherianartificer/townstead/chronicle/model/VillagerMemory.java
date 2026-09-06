package com.aetherianartificer.townstead.chronicle.model;


import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.aetherianartificer.townstead.social.SocialMemoryDefinition;

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
    private final String operationId;
    private final String source;
    private final int halfLifeDays;
    private final float forgetBelow;
    private final int retentionPriority;
    private final boolean episodic;

    public VillagerMemory(String memoryKey, @Nullable UUID otherParty, long day,
                          float strength, float valence, Map<String, String> params) {
        this(memoryKey, otherParty, day, strength, valence, params, "", "townstead:legacy_memory",
                -1, 0.05f, 10, false);
    }

    public VillagerMemory(String memoryKey, @Nullable UUID otherParty, long day,
                          float strength, float valence, Map<String, String> params,
                          String operationId, String source, SocialMemoryDefinition definition) {
        this(memoryKey, otherParty, day, strength, valence, params, operationId, source,
                definition.halfLifeDays(), definition.forgetBelow(), definition.retentionPriority(), true);
    }

    private VillagerMemory(String memoryKey, @Nullable UUID otherParty, long day,
                           float strength, float valence, Map<String, String> params,
                           String operationId, String source, int halfLifeDays, float forgetBelow,
                           int retentionPriority, boolean episodic) {
        this.memoryKey = memoryKey;
        this.otherParty = otherParty;
        this.firstDay = day;
        this.lastDay = day;
        this.count = 1;
        this.strength = strength;
        this.valence = valence;
        this.params = params == null ? new HashMap<>() : new HashMap<>(params);
        this.operationId = operationId == null ? "" : operationId;
        this.source = source == null ? "" : source;
        this.halfLifeDays = halfLifeDays;
        this.forgetBelow = forgetBelow;
        this.retentionPriority = retentionPriority;
        this.episodic = episodic;
    }

    public String memoryKey() { return memoryKey; }
    public @Nullable UUID otherParty() { return otherParty; }
    public long firstDay() { return firstDay; }
    public long lastDay() { return lastDay; }
    public int count() { return count; }
    public float strength() { return strength; }
    public float valence() { return valence; }
    public Map<String, String> params() { return params; }
    public String operationId() { return operationId; }
    public String source() { return source; }
    public int halfLifeDays() { return halfLifeDays; }
    public float forgetBelow() { return forgetBelow; }
    public int retentionPriority() { return retentionPriority; }
    public boolean episodic() { return episodic; }
    public boolean matchesOperation(String operation) { return episodic && operationId.equals(operation); }

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

    /** Applies the forgetting policy captured when this episode was formed. */
    public void decayDaily(float legacyFactor) {
        if (halfLifeDays == 0) return;
        strength *= halfLifeDays < 0 ? legacyFactor : (float) Math.pow(0.5, 1.0 / halfLifeDays);
    }

    public boolean forgotten(float legacyThreshold) {
        return strength < (episodic ? forgetBelow : legacyThreshold);
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
        if (episodic) {
            tag.putBoolean("episodic", true);
            tag.putString("operation", operationId);
            tag.putString("source", source);
            tag.putInt("halfLife", halfLifeDays);
            tag.putFloat("forgetBelow", forgetBelow);
            tag.putInt("retentionPriority", retentionPriority);
        }
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
        boolean episodic = tag.getBoolean("episodic");
        VillagerMemory memory = new VillagerMemory(tag.getString("key"),
                tag.hasUUID("other") ? tag.getUUID("other") : null, tag.getLong("first"),
                tag.getFloat("strength"), tag.getFloat("valence"), params,
                episodic ? tag.getString("operation") : "",
                episodic ? tag.getString("source") : "townstead:legacy_memory",
                episodic ? Math.max(0, tag.getInt("halfLife")) : -1,
                episodic && tag.contains("forgetBelow") ? Math.max(0, tag.getFloat("forgetBelow")) : 0.05f,
                episodic && tag.contains("retentionPriority") ? Math.max(0, Math.min(100, tag.getInt("retentionPriority"))) : 10,
                episodic);
        memory.lastDay = tag.getLong("last");
        memory.count = Math.max(1, tag.getInt("count"));
        return memory;
    }
}
