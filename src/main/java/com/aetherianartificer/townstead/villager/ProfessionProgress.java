package com.aetherianartificer.townstead.villager;

/**
 * Shared profession-XP engine. Operates on the typed {@link ProfessionXp}
 * held behind a {@link ProfessionXpStore} (implemented by
 * {@link TownsteadVillager.ProfessionMemory}).
 *
 * <p>The tier thresholds, daily cap, and XP ceiling come from a {@link ProgressionSpec} resolved
 * by {@link ProfessionProgressions}: a data-driven {@link com.aetherianartificer.townstead.profession.def.ProfessionDef}
 * when one is registered for the profession id, otherwise the built-in {@link ProfessionXpType}.
 * With no datapack override the four built-ins behave exactly as before (a daily XP cap, five
 * XP-gated tiers, and a tier-up timestamp). {@link #getTier} lazily backfills the stored tier
 * from XP for legacy/uninitialised data, persisting the result like the originals did.
 */
public final class ProfessionProgress {
    private ProfessionProgress() {}

    public static int getXp(ProfessionXpStore store, ProfessionXpType type) {
        return Math.max(0, store.professionXp(type.id()).xp());
    }

    public static int getTier(ProfessionXpStore store, ProfessionXpType type) {
        return getTier(store, type.id(), ProfessionProgressions.spec(type));
    }

    public static long getLastTierUpTick(ProfessionXpStore store, ProfessionXpType type) {
        return store.professionXp(type.id()).lastTierUpTick();
    }

    public static int getXpToNextTier(ProfessionXpStore store, ProfessionXpType type) {
        ProgressionSpec spec = ProfessionProgressions.spec(type);
        int tier = getTier(store, type.id(), spec);
        if (tier >= spec.maxTier()) return 0;
        int xp = Math.max(0, store.professionXp(type.id()).xp());
        return Math.max(0, spec.thresholdForTier(tier) - xp);
    }

    public static GainResult addXp(ProfessionXpStore store, ProfessionXpType type, int requested, long gameTime) {
        return addXp(store, type.id(), ProfessionProgressions.spec(type), requested, gameTime);
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
