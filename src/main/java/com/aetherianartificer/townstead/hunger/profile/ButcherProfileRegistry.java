package com.aetherianartificer.townstead.hunger.profile;

import com.aetherianartificer.townstead.Townstead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ButcherProfileRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/ButcherProfileRegistry");
    public static final String DEFAULT_PROFILE_ID = "smokehouse_core";

    private static final Map<String, ButcherProfileDefinition> BUILTIN_PROFILES = new LinkedHashMap<>();
    private static final Map<String, ButcherProfileDefinition> DATAPACK_PROFILES = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private ButcherProfileRegistry() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        BUILTIN_PROFILES.clear();
        DATAPACK_PROFILES.clear();
        bootstrapped = true;
        LOGGER.info("Butcher profile registry bootstrapped (profiles load from datapacks/resources)");
    }

    public static synchronized boolean register(ButcherProfileDefinition profile, boolean builtin) {
        if (profile == null) return false;
        List<String> errors = profile.validate();
        if (!errors.isEmpty()) {
            LOGGER.warn("Rejected butcher profile '{}': {}", profile.id(), String.join("; ", errors));
            return false;
        }
        if (builtin) {
            BUILTIN_PROFILES.put(profile.id(), profile);
        } else {
            DATAPACK_PROFILES.put(profile.id(), profile);
            LOGGER.info(
                    "Registered butcher profile '{}', family={}, level={}, requiredTier={}",
                    profile.id(),
                    profile.family(),
                    profile.level(),
                    profile.requiredTier()
            );
        }
        return true;
    }

    public static synchronized void clearDatapackProfiles() {
        bootstrap();
        DATAPACK_PROFILES.clear();
    }

    public static synchronized ButcherProfileDefinition resolveOrDefault(String id) {
        return resolveForTier(id, 5);
    }

    public static synchronized ButcherProfileDefinition resolveForTier(String id, int maxTier) {
        bootstrap();
        int normalizedTier = Math.max(1, Math.min(5, maxTier));
        Map<String, ButcherProfileDefinition> merged = merged();

        String requested = id == null ? "" : id.trim();
        ButcherProfileDefinition exact = requested.isEmpty() ? null : merged.get(requested);
        if (exact != null && exact.requiredTier() <= normalizedTier) {
            ButcherProfileDefinition familyBest = bestByFamily(merged.values(), exact.family(), normalizedTier);
            return familyBest != null ? familyBest : exact;
        }
        if (exact != null) {
            ButcherProfileDefinition familyBest = bestByFamily(merged.values(), exact.family(), normalizedTier);
            if (familyBest != null) return familyBest;
        }

        if (!requested.isEmpty()) {
            ButcherProfileDefinition familyBest = bestByFamily(merged.values(), requested, normalizedTier);
            if (familyBest != null) return familyBest;
        }

        ButcherProfileDefinition defaultFamilyBest = bestByFamily(merged.values(), DEFAULT_PROFILE_ID, normalizedTier);
        if (defaultFamilyBest != null) return defaultFamilyBest;

        ButcherProfileDefinition bestAny = null;
        for (ButcherProfileDefinition candidate : merged.values()) {
            if (candidate.requiredTier() > normalizedTier) continue;
            if (bestAny == null
                    || candidate.requiredTier() > bestAny.requiredTier()
                    || (candidate.requiredTier() == bestAny.requiredTier() && candidate.level() > bestAny.level())) {
                bestAny = candidate;
            }
        }
        if (bestAny != null) return bestAny;

        return ButcherProfileFallbacks.defaultL1();
    }

    public static synchronized Collection<ButcherProfileDefinition> all() {
        bootstrap();
        return List.copyOf(merged().values());
    }

    private static LinkedHashMap<String, ButcherProfileDefinition> merged() {
        LinkedHashMap<String, ButcherProfileDefinition> merged = new LinkedHashMap<>();
        merged.putAll(BUILTIN_PROFILES);
        merged.putAll(DATAPACK_PROFILES);
        return merged;
    }

    private static ButcherProfileDefinition bestByFamily(
            Iterable<ButcherProfileDefinition> profiles,
            String family,
            int maxTier
    ) {
        if (family == null || family.isBlank()) return null;
        ButcherProfileDefinition best = null;
        for (ButcherProfileDefinition candidate : profiles) {
            if (!family.equals(candidate.family())) continue;
            if (candidate.requiredTier() > maxTier) continue;
            if (best == null
                    || candidate.level() > best.level()
                    || (candidate.level() == best.level() && candidate.requiredTier() > best.requiredTier())) {
                best = candidate;
            }
        }
        return best;
    }
}
