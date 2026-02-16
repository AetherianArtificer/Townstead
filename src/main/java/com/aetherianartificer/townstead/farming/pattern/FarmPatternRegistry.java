package com.aetherianartificer.townstead.farming.pattern;

import com.aetherianartificer.townstead.Townstead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FarmPatternRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/FarmPatternRegistry");
    public static final String DEFAULT_PATTERN_ID = "starter_rows";

    private static final Map<String, FarmPatternDefinition> BUILTIN_PATTERNS = new LinkedHashMap<>();
    private static final Map<String, FarmPatternDefinition> DATAPACK_PATTERNS = new LinkedHashMap<>();
    private static boolean bootstrapped;

    private FarmPatternRegistry() {}

    public static synchronized void bootstrap() {
        if (bootstrapped) return;
        BUILTIN_PATTERNS.clear();
        DATAPACK_PATTERNS.clear();
        bootstrapped = true;
        LOGGER.info("Farm pattern registry bootstrapped (patterns load from datapacks/resources)");
    }

    public static synchronized boolean register(FarmPatternDefinition pattern, boolean builtin) {
        if (pattern == null) return false;
        List<String> errors = pattern.validate();
        if (!errors.isEmpty()) {
            LOGGER.warn("Rejected farm pattern '{}': {}", pattern.id(), String.join("; ", errors));
            return false;
        }
        if (builtin) {
            BUILTIN_PATTERNS.put(pattern.id(), pattern);
        } else {
            DATAPACK_PATTERNS.put(pattern.id(), pattern);
        }
        if (!builtin) {
            LOGGER.info(
                    "Registered farm pattern '{}', plannerType={}, requiredTier={}, family={}, level={}",
                    pattern.id(),
                    pattern.plannerType(),
                    pattern.requiredTier(),
                    pattern.family(),
                    pattern.level()
            );
        }
        return true;
    }

    public static synchronized void clearDatapackPatterns() {
        bootstrap();
        DATAPACK_PATTERNS.clear();
    }

    public static synchronized FarmPatternDefinition resolveOrDefault(String id) {
        return resolveForTier(id, 5);
    }

    public static synchronized FarmPatternDefinition resolveForTier(String id, int maxTier) {
        bootstrap();
        int normalizedTier = Math.max(1, Math.min(5, maxTier));
        Map<String, FarmPatternDefinition> merged = merged();

        String requested = id == null ? "" : id.trim();
        FarmPatternDefinition exact = requested.isEmpty() ? null : merged.get(requested);
        if (exact != null && exact.requiredTier() <= normalizedTier) {
            FarmPatternDefinition familyBest = bestByFamily(merged.values(), exact.family(), normalizedTier);
            return familyBest != null ? familyBest : exact;
        }
        if (exact != null) {
            FarmPatternDefinition familyBest = bestByFamily(merged.values(), exact.family(), normalizedTier);
            if (familyBest != null) return familyBest;
        }

        // If caller requested a family id (e.g. "starter_rows"), return best unlocked level in that family.
        if (!requested.isEmpty()) {
            FarmPatternDefinition familyBest = bestByFamily(merged.values(), requested, normalizedTier);
            if (familyBest != null) return familyBest;
        }

        FarmPatternDefinition defaultFamilyBest = bestByFamily(merged.values(), DEFAULT_PATTERN_ID, normalizedTier);
        if (defaultFamilyBest != null) return defaultFamilyBest;

        FarmPatternDefinition bestAny = null;
        for (FarmPatternDefinition candidate : merged.values()) {
            if (candidate.requiredTier() > normalizedTier) continue;
            if (bestAny == null
                    || candidate.requiredTier() > bestAny.requiredTier()
                    || (candidate.requiredTier() == bestAny.requiredTier() && candidate.level() > bestAny.level())) {
                bestAny = candidate;
            }
        }
        if (bestAny != null) return bestAny;

        // Emergency fallback to keep runtime safe if datapack/resource loading fails.
        return new FarmPatternDefinition(DEFAULT_PATTERN_ID + "_l1", DEFAULT_PATTERN_ID, 1, DEFAULT_PATTERN_ID, 1);
    }

    public static synchronized Collection<FarmPatternDefinition> all() {
        bootstrap();
        return List.copyOf(merged().values());
    }

    private static LinkedHashMap<String, FarmPatternDefinition> merged() {
        LinkedHashMap<String, FarmPatternDefinition> merged = new LinkedHashMap<>();
        merged.putAll(BUILTIN_PATTERNS);
        merged.putAll(DATAPACK_PATTERNS);
        return merged;
    }

    private static FarmPatternDefinition bestByFamily(Iterable<FarmPatternDefinition> patterns, String family, int maxTier) {
        if (family == null || family.isBlank()) return null;
        FarmPatternDefinition best = null;
        for (FarmPatternDefinition candidate : patterns) {
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
