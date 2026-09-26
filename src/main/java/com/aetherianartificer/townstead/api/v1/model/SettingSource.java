package com.aetherianartificer.townstead.api.v1.model;

/**
 * Where the value of a setting in effect comes from: this world's own choice, the modpack's
 * defaults, the config file, or Townstead's built-in default. Added in API revision 2.
 */
public enum SettingSource {
    WORLD("world"),
    MODPACK("modpack"),
    CONFIG("config"),
    DEFAULT("default");

    private final String id;

    SettingSource(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static SettingSource byId(String id) {
        for (SettingSource source : values()) {
            if (source.id.equals(id)) return source;
        }
        return DEFAULT;
    }
}
