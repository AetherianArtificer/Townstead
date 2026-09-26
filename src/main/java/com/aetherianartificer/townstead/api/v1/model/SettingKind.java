package com.aetherianartificer.townstead.api.v1.model;

/** What sort of value a setting holds. Added in API revision 2. */
public enum SettingKind {
    BOOLEAN("boolean"),
    INTEGER("integer"),
    DECIMAL("decimal"),
    CHOICE("choice"),
    TEXT("text"),
    LIST("list");

    private final String id;

    SettingKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SettingKind byId(String id) {
        for (SettingKind kind : values()) {
            if (kind.id.equals(id)) return kind;
        }
        return TEXT;
    }
}
