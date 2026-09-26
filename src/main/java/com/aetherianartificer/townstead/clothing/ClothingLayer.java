package com.aetherianartificer.townstead.clothing;

import org.jetbrains.annotations.Nullable;

/**
 * The four layers a villager wears. A skin is the base, item stacks are outerwear or armour, and
 * accessories sit in Curios slots or come from records that are not stacks at all.
 */
public enum ClothingLayer {
    BASE("base"),
    OUTERWEAR("outerwear"),
    ARMOUR("armour"),
    ACCESSORY("accessory");

    private final String key;

    ClothingLayer(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** Accepts the American spelling too; a pack author should not have to know which we chose. */
    public static @Nullable ClothingLayer parse(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.equals("armor")) return ARMOUR;
        for (ClothingLayer layer : values()) {
            if (layer.key.equals(value)) return layer;
        }
        return null;
    }
}
