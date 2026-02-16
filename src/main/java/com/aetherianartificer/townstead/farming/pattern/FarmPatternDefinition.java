package com.aetherianartificer.townstead.farming.pattern;

import java.util.List;

public record FarmPatternDefinition(String id, String plannerType) {
    public List<String> validate() {
        java.util.ArrayList<String> errors = new java.util.ArrayList<>();
        if (id == null || id.isBlank()) errors.add("id is required");
        if (plannerType == null || plannerType.isBlank()) errors.add("plannerType is required");
        return errors;
    }
}
