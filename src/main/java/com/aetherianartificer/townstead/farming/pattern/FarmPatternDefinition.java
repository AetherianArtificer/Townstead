package com.aetherianartificer.townstead.farming.pattern;

import java.util.List;

public record FarmPatternDefinition(String id, String plannerType, int requiredTier, String family, int level) {
    public List<String> validate() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        if (id == null || id.isBlank()) errors.add("id is required");
        if (plannerType == null || plannerType.isBlank()) errors.add("plannerType is required");
        if (requiredTier < 1 || requiredTier > 5) errors.add("requiredTier must be in range 1..5");
        if (family == null || family.isBlank()) errors.add("family is required");
        if (level < 1 || level > 5) errors.add("level must be in range 1..5");
        return errors;
    }
}
