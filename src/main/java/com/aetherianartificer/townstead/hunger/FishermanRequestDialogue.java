package com.aetherianartificer.townstead.hunger;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.util.RandomSource;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Picks the right translation key for a fisherman's request-chat line,
 * preferring a personality-flavored variant when we ship one for that
 * (personality × state) pair. Returns the vanilla generic key as a
 * fallback so nothing ever resolves to a raw translation key.
 *
 * Keys follow the pattern:
 *   dialogue.chat.fisherman_request.<state>/<n>                — generic
 *   dialogue.chat.fisherman_request.<state>.<personality>/<n>  — flavored
 *
 * The {@link #COVERED_PERSONALITIES} map is the single source of truth
 * on the server side for whether a flavored key exists — it must stay
 * in sync with what the lang JSON actually defines. Adding a new
 * personality variant means (a) entries in mca_dialogue/lang/en_us.json
 * and (b) extending this map.
 */
public final class FishermanRequestDialogue {
    /** Per-personality, per-state count of flavored variants we ship. */
    private static final Map<Personality, Map<String, Integer>> COVERED_PERSONALITIES;
    static {
        java.util.HashMap<Personality, Map<String, Integer>> m = new java.util.HashMap<>();
        // Personalities present on both 1.20.1 Forge MCA and 1.21.1 NeoForge MCA.
        m.put(Personality.FLIRTY,  stateCounts(3, 3, 3, 3));
        m.put(Personality.GLOOMY,  stateCounts(3, 3, 3, 3));
        m.put(Personality.GREEDY,  stateCounts(3, 3, 3, 3));
        m.put(Personality.ODD,     stateCounts(3, 3, 3, 3));
        //? if neoforge {
        // Personalities only in the 1.21.1 NeoForge MCA enum.
        m.put(Personality.CRABBY,  stateCounts(3, 3, 3, 3));
        m.put(Personality.RELAXED, stateCounts(3, 3, 3, 3));
        m.put(Personality.PLAYFUL, stateCounts(3, 3, 3, 3));
        //?}
        COVERED_PERSONALITIES = java.util.Collections.unmodifiableMap(m);
    }

    private static Map<String, Integer> stateCounts(int noRod, int noWater, int noStorage, int unreachable) {
        return Map.of(
                "no_rod", noRod,
                "no_water", noWater,
                "no_storage", noStorage,
                "unreachable", unreachable
        );
    }

    private static final Map<String, Integer> GENERIC_COUNTS = Map.of(
            "no_rod", 6,
            "no_water", 6,
            "no_storage", 4,
            "unreachable", 4,
            // One-shot acknowledgment chat for the first time a villager casts
            // from inside a dock's bounds. Persisted via LongTermMemory, not
            // a blocked-reason state — see FishermanWorkTask#townstead$maybeAnnounceFirstDockUse.
            "dock_first_use", 3
    );

    private FishermanRequestDialogue() {}

    /**
     * Build the translation key for a request chat line. Prefers a
     * personality-flavored variant when the villager's personality has one
     * shipped for this state; otherwise returns the generic variant.
     */
    public static String pickKey(VillagerEntityMCA villager, String state, RandomSource random) {
        Personality personality = safePersonality(villager);
        Map<String, Integer> flavored = COVERED_PERSONALITIES.get(personality);
        Integer flavoredCount = flavored == null ? null : flavored.get(state);
        if (flavoredCount != null && flavoredCount > 0) {
            int n = 1 + random.nextInt(flavoredCount);
            return "dialogue.chat.fisherman_request." + state + "." +
                    personality.name().toLowerCase(Locale.ROOT) + "/" + n;
        }
        Integer genericCount = GENERIC_COUNTS.get(state);
        int n = 1 + random.nextInt(Math.max(1, genericCount == null ? 1 : genericCount));
        return "dialogue.chat.fisherman_request." + state + "/" + n;
    }

    private static Personality safePersonality(VillagerEntityMCA villager) {
        try {
            Personality p = villager.getVillagerBrain().getPersonality();
            return p == null ? Personality.UNASSIGNED : p;
        } catch (Throwable t) {
            return Personality.UNASSIGNED;
        }
    }

    /** Personalities that have any flavored variants — exposed for tooling. */
    public static Set<Personality> coveredPersonalities() {
        return COVERED_PERSONALITIES.keySet();
    }
}
