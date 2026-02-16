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
            LOGGER.info("Registered farm pattern '{}', plannerType={}", pattern.id(), pattern.plannerType());
        }
        return true;
    }

    public static synchronized void clearDatapackPatterns() {
        bootstrap();
        DATAPACK_PATTERNS.clear();
    }

    public static synchronized FarmPatternDefinition resolveOrDefault(String id) {
        bootstrap();
        FarmPatternDefinition pattern = null;
        if (id != null) {
            pattern = DATAPACK_PATTERNS.get(id);
            if (pattern == null) {
                pattern = BUILTIN_PATTERNS.get(id);
            }
        }
        if (pattern != null) return pattern;
        FarmPatternDefinition datapackDefault = DATAPACK_PATTERNS.get(DEFAULT_PATTERN_ID);
        if (datapackDefault != null) return datapackDefault;
        // Emergency fallback to keep runtime safe if datapack/resource loading fails.
        return new FarmPatternDefinition(DEFAULT_PATTERN_ID, DEFAULT_PATTERN_ID);
    }

    public static synchronized Collection<FarmPatternDefinition> all() {
        bootstrap();
        LinkedHashMap<String, FarmPatternDefinition> merged = new LinkedHashMap<>();
        merged.putAll(BUILTIN_PATTERNS);
        merged.putAll(DATAPACK_PATTERNS);
        return List.copyOf(merged.values());
    }
}
