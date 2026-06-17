package com.aetherianartificer.townstead.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/** Shared validation for versioned Townstead data-pack documents. */
public final class TownsteadSchema {
    private TownsteadSchema() {
    }

    public static void validate(JsonObject document, String expected) {
        if (document == null || !document.has("schema")) return;

        JsonElement value = document.get("schema");
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("'schema' must be a string");
        }

        String actual = value.getAsString();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Expected schema '" + expected + "', got '" + actual + "'");
        }
    }
}
