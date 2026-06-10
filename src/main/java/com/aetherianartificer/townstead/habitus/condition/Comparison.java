package com.aetherianartificer.townstead.habitus.condition;

import java.util.Locale;

/**
 * Apoli/Apugli's {@code comparison} data type: a relational operator chosen by a
 * JSON string ({@code ==}, {@code !=}, {@code <}, {@code <=}, {@code >}, {@code >=},
 * with word aliases). Used by {@code compare_resource}, {@code entity_in_radius} and
 * other count/value gates.
 */
public enum Comparison {
    EQUAL, NOT_EQUAL, LESS, LESS_OR_EQUAL, GREATER, GREATER_OR_EQUAL;

    public boolean compare(double a, double b) {
        return switch (this) {
            case EQUAL -> a == b;
            case NOT_EQUAL -> a != b;
            case LESS -> a < b;
            case LESS_OR_EQUAL -> a <= b;
            case GREATER -> a > b;
            case GREATER_OR_EQUAL -> a >= b;
        };
    }

    /** Parse a comparison string, defaulting to {@code >=} for an unknown or missing value. */
    public static Comparison parse(String raw) {
        if (raw == null) return GREATER_OR_EQUAL;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "==", "=", "equal" -> EQUAL;
            case "!=", "<>", "not_equal" -> NOT_EQUAL;
            case "<", "less" -> LESS;
            case "<=", "less_or_equal" -> LESS_OR_EQUAL;
            case ">", "greater" -> GREATER;
            default -> GREATER_OR_EQUAL;
        };
    }
}
