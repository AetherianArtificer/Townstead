package com.aetherianartificer.townstead.recognition;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-pack policy for the physical form in which an MCA building type may be recognised.
 *
 * <p>MCA makes {@code grouped} types outdoor-only and every other type room-only. Townstead's
 * {@code enclosure} property deliberately sits above that binary: an optional type may be stored
 * as a normal MCA room or as an external building while retaining one logical type everywhere
 * else.</p>
 */
public final class BuildingEnclosurePolicies {
    public enum Mode {
        REQUIRED,
        OPTIONAL,
        NONE;

        public static Mode parse(String raw) {
            if (raw == null) return REQUIRED;
            return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
                case "required" -> REQUIRED;
                case "optional" -> OPTIONAL;
                case "none" -> NONE;
                default -> throw new IllegalArgumentException(
                        "'enclosure' must be 'required', 'optional', or 'none'");
            };
        }

        public boolean allowsOpenAir() {
            return this != REQUIRED;
        }

        public boolean allowsRoom() {
            return this != NONE;
        }
    }

    private static volatile Map<String, Mode> MODES = Map.of();

    private BuildingEnclosurePolicies() {}

    public static void replaceAll(Map<String, Mode> next) {
        Map<String, Mode> stable = new LinkedHashMap<>();
        next.forEach((type, mode) -> {
            if (type != null && !type.isBlank() && mode != null && mode != Mode.REQUIRED) {
                stable.put(type, mode);
            }
        });
        MODES = Map.copyOf(stable);
    }

    public static Mode modeOf(String buildingType) {
        return buildingType == null ? Mode.REQUIRED : MODES.getOrDefault(buildingType, Mode.REQUIRED);
    }

    public static boolean allowsOpenAir(String buildingType) {
        return modeOf(buildingType).allowsOpenAir();
    }

    public static Map<String, Mode> snapshot() {
        return MODES;
    }
}
