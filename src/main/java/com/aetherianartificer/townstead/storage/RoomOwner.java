package com.aetherianartificer.townstead.storage;

import java.util.UUID;

/** A player-selected person who may use private storage in one room or building. */
public record RoomOwner(UUID uuid, String name, Kind kind) {
    public RoomOwner {
        if (uuid == null) throw new IllegalArgumentException("Room owner UUID is required");
        name = name == null ? "" : name;
        kind = kind == null ? Kind.VILLAGER : kind;
    }

    public enum Kind {
        VILLAGER,
        PLAYER;

        public static Kind parse(String raw) {
            if (raw == null) return VILLAGER;
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return VILLAGER;
            }
        }
    }
}
