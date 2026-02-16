package com.aetherianartificer.townstead.hunger;

import net.minecraft.nbt.CompoundTag;

public final class FarmerProgressData {
    private static final String KEY_XP = "farmerXp";
    private static final String KEY_TIER = "farmerTier";
    private static final String KEY_LAST_TIER_UP_TICK = "farmerLastTierUpTick";
    private static final String KEY_XP_DAY = "farmerXpDay";
    private static final String KEY_XP_TODAY = "farmerXpToday";

    private static final int[] TIER_XP_THRESHOLDS = {0, 120, 320, 700, 1300};
    private static final int DAILY_XP_CAP = 240;
    private static final int MAX_XP = 200000;

    private FarmerProgressData() {}

    public static int getXp(CompoundTag tag) {
        return Math.max(0, tag.getInt(KEY_XP));
    }

    public static int getTier(CompoundTag tag) {
        int raw = tag.getInt(KEY_TIER);
        if (raw <= 0) {
            raw = tierForXp(getXp(tag));
            setTier(tag, raw);
        }
        return Math.max(1, Math.min(5, raw));
    }

    public static long getLastTierUpTick(CompoundTag tag) {
        return tag.getLong(KEY_LAST_TIER_UP_TICK);
    }

    public static int getXpToNextTier(CompoundTag tag) {
        int tier = getTier(tag);
        if (tier >= 5) return 0;
        int xp = getXp(tag);
        int nextThreshold = TIER_XP_THRESHOLDS[tier];
        return Math.max(0, nextThreshold - xp);
    }

    public static GainResult addXp(CompoundTag tag, int requested, long gameTime) {
        if (requested <= 0) return new GainResult(0, getTier(tag), getTier(tag), false);
        long day = gameTime / 24000L;
        long storedDay = tag.getLong(KEY_XP_DAY);
        int gainedToday = Math.max(0, tag.getInt(KEY_XP_TODAY));
        if (storedDay != day) {
            storedDay = day;
            gainedToday = 0;
        }

        int allowance = Math.max(0, DAILY_XP_CAP - gainedToday);
        int applied = Math.min(requested, allowance);
        int beforeTier = getTier(tag);
        if (applied <= 0) {
            tag.putLong(KEY_XP_DAY, storedDay);
            tag.putInt(KEY_XP_TODAY, gainedToday);
            return new GainResult(0, beforeTier, beforeTier, false);
        }

        int xp = Math.max(0, Math.min(MAX_XP, getXp(tag) + applied));
        gainedToday += applied;
        int afterTier = tierForXp(xp);
        boolean tierUp = afterTier > beforeTier;

        setXp(tag, xp);
        setTier(tag, afterTier);
        tag.putLong(KEY_XP_DAY, storedDay);
        tag.putInt(KEY_XP_TODAY, gainedToday);
        if (tierUp) {
            tag.putLong(KEY_LAST_TIER_UP_TICK, gameTime);
        }
        return new GainResult(applied, beforeTier, afterTier, tierUp);
    }

    private static void setXp(CompoundTag tag, int value) {
        tag.putInt(KEY_XP, Math.max(0, Math.min(MAX_XP, value)));
    }

    private static void setTier(CompoundTag tag, int value) {
        tag.putInt(KEY_TIER, Math.max(1, Math.min(5, value)));
    }

    private static int tierForXp(int xp) {
        if (xp >= TIER_XP_THRESHOLDS[4]) return 5;
        if (xp >= TIER_XP_THRESHOLDS[3]) return 4;
        if (xp >= TIER_XP_THRESHOLDS[2]) return 3;
        if (xp >= TIER_XP_THRESHOLDS[1]) return 2;
        return 1;
    }

    public record GainResult(int appliedXp, int tierBefore, int tierAfter, boolean tierUp) {}
}
