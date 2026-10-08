package com.aetherianartificer.townstead.storage;

import java.util.Locale;

/** The MCA place covered by an ownership deed. */
public enum OwnershipScope {
    ROOM,
    BUILDING;

    public static OwnershipScope parse(String raw) {
        if (raw == null || raw.isBlank()) return ROOM;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ROOM;
        }
    }
}
