package com.aetherianartificer.townstead.villager;

import net.minecraft.resources.ResourceLocation;

/**
 * Shared career-XP engine. Operates on the typed {@link ProfessionXp}
 * held behind a {@link ProfessionXpStore} (implemented by
 * {@link TownsteadVillager.ProfessionMemory} and the player Career profile).
 *
 * <p>Careers are keyed by the profession's registry id (e.g. {@code minecraft:farmer},
 * {@code townstead:cook}); the tier thresholds, daily cap, and XP ceiling come from the
 * career's data-pack def via {@link ProfessionProgressions}. Storage keys are the full id
 * string; the stores themselves fall back to bare legacy keys ({@code "farmer"}) written by
 * earlier versions. {@link #getTier} lazily backfills the stored tier from XP for
 * legacy/uninitialised data, persisting the result like the originals did.</p>
 */
public final class ProfessionProgress {
    private ProfessionProgress() {}

    public static int getXp(ProfessionXpStore store, ResourceLocation careerId) {
        return Math.max(0, store.professionXp(key(careerId)).xp());
    }

    public static int getTier(ProfessionXpStore store, ResourceLocation careerId) {
        careerId = canonical(careerId);
        return getTier(store, careerId.toString(), ProfessionProgressions.spec(careerId));
    }

    public static long getLastTierUpTick(ProfessionXpStore store, ResourceLocation careerId) {
        return store.professionXp(key(careerId)).lastTierUpTick();
    }

    public static int getXpToNextTier(ProfessionXpStore store, ResourceLocation careerId) {
        careerId = canonical(careerId);
        ProgressionSpec spec = ProfessionProgressions.spec(careerId);
        int tier = getTier(store, careerId.toString(), spec);
        if (tier >= spec.maxTier()) return 0;
        int xp = Math.max(0, store.professionXp(careerId.toString()).xp());
        return Math.max(0, spec.thresholdForTier(tier) - xp);
    }

    /**
     * Admin/debug only: place a career at an exact level, returning the level actually reached
     * after clamping to the track. Moves the XP total as well as the stored tier, because every
     * later gain recomputes the tier from XP and a tier set on its own would snap straight back.
     * Nothing in normal play calls this; levels are earned through {@link #addXp}.
     */
    public static int setLevel(ProfessionXpStore store, ResourceLocation careerId, int level) {
        careerId = canonical(careerId);
        ProgressionSpec spec = ProfessionProgressions.spec(careerId);
        int clamped = Math.max(1, Math.min(spec.maxTier(), level));
        ProfessionXp state = store.professionXp(careerId.toString());
        int xp = Math.max(0, Math.min(spec.maxXp(), spec.thresholdForTier(clamped - 1)));
        store.setProfessionXp(careerId.toString(), new ProfessionXp(
                xp, clamped, state.lastTierUpTick(), state.xpDay(), state.xpToday()));
        return clamped;
    }

    public static GainResult addXp(ProfessionXpStore store, ResourceLocation careerId, int requested, long gameTime) {
        careerId = canonical(careerId);
        return addXp(store, careerId.toString(), ProfessionProgressions.spec(careerId), requested, gameTime);
    }

    /** Alias ids converge on their def's primary id so history never fragments per source mod. */
    private static ResourceLocation canonical(ResourceLocation careerId) {
        return com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(careerId);
    }

    private static String key(ResourceLocation careerId) {
        return canonical(careerId).toString();
    }

    private static int getTier(ProfessionXpStore store, String professionId, ProgressionSpec spec) {
        ProfessionXp state = store.professionXp(professionId);
        int raw = state.tier();
        if (raw <= 0) {
            raw = spec.tierForXp(Math.max(0, state.xp()));
            store.setProfessionXp(professionId, state.withTier(raw));
        }
        return Math.max(1, Math.min(spec.maxTier(), raw));
    }

    private static GainResult addXp(ProfessionXpStore store, String professionId, ProgressionSpec spec,
                                    int requested, long gameTime) {
        int beforeTier = getTier(store, professionId, spec);
        if (requested <= 0) return new GainResult(0, beforeTier, beforeTier, false);

        ProfessionXp state = store.professionXp(professionId);
        long day = gameTime / 24000L;
        long storedDay = state.xpDay();
        int gainedToday = Math.max(0, state.xpToday());
        if (storedDay != day) {
            storedDay = day;
            gainedToday = 0;
        }

        int allowance = Math.max(0, spec.dailyXpCap() - gainedToday);
        int applied = Math.min(requested, allowance);
        if (applied <= 0) {
            store.setProfessionXp(professionId,
                    new ProfessionXp(state.xp(), state.tier(), state.lastTierUpTick(), storedDay, gainedToday));
            return new GainResult(0, beforeTier, beforeTier, false);
        }

        int xp = Math.max(0, Math.min(spec.maxXp(), state.xp() + applied));
        gainedToday += applied;
        int afterTier = spec.tierForXp(xp);
        boolean tierUp = afterTier > beforeTier;
        long lastTierUpTick = tierUp ? gameTime : state.lastTierUpTick();

        store.setProfessionXp(professionId, new ProfessionXp(xp, afterTier, lastTierUpTick, storedDay, gainedToday));
        return new GainResult(applied, beforeTier, afterTier, tierUp);
    }

    public record GainResult(int appliedXp, int tierBefore, int tierAfter, boolean tierUp) {}
}
