package com.aetherianartificer.townstead.chronicle.model;

import net.minecraft.nbt.CompoundTag;

/**
 * One villager's feeling toward another, accumulated from believed accounts.
 * {@code sourceAccountId} points at the account that last moved it, so future
 * discredit/retraction can reverse contributions through the chain.
 */
public final class SentimentEntry {

    private float value;
    private long lastDay;
    private long sourceAccountId;

    public SentimentEntry(float value, long lastDay, long sourceAccountId) {
        this.value = value;
        this.lastDay = lastDay;
        this.sourceAccountId = sourceAccountId;
    }

    public float value() { return value; }
    public long lastDay() { return lastDay; }
    public long sourceAccountId() { return sourceAccountId; }

    public void adjust(float delta, long day, long accountId) {
        value += delta;
        lastDay = Math.max(lastDay, day);
        sourceAccountId = accountId;
    }

    public void decay(float factor) {
        value *= factor;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("v", value);
        tag.putLong("day", lastDay);
        tag.putLong("src", sourceAccountId);
        return tag;
    }

    public static SentimentEntry load(CompoundTag tag) {
        return new SentimentEntry(tag.getFloat("v"), tag.getLong("day"), tag.getLong("src"));
    }
}
