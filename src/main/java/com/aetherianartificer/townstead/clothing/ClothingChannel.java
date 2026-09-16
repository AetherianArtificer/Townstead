package com.aetherianartificer.townstead.clothing;

import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

/**
 * Where on the body a piece sits. Presentation conflicts exist only within one channel, and the
 * dress behaviour fills channels, so this is the unit both of them reason about. {@link #ALL} is
 * the whole-body channel a base skin occupies.
 */
public enum ClothingChannel {
    HEAD("head"),
    BODY("body"),
    LEGS("legs"),
    FEET("feet"),
    BACK("back"),
    BELT("belt"),
    NECK("neck"),
    HANDS("hands"),
    ALL("all");

    private final String key;

    ClothingChannel(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static @Nullable ClothingChannel parse(@Nullable String raw) {
        if (raw == null) return null;
        String value = raw.trim().toLowerCase(java.util.Locale.ROOT);
        for (ClothingChannel channel : values()) {
            if (channel.key.equals(value)) return channel;
        }
        return null;
    }

    public static ClothingChannel of(EquipmentSlot slot) {
        switch (slot) {
            case HEAD: return HEAD;
            case CHEST: return BODY;
            case LEGS: return LEGS;
            case FEET: return FEET;
            default: return HANDS;
        }
    }

    /**
     * Curios and Trinkets slot ids as the mods that register them spell them. Unknown ids fall to
     * null rather than a guess so a bracelet is never mistaken for a hat.
     */
    public static @Nullable ClothingChannel ofCurioSlot(@Nullable String slotId) {
        if (slotId == null) return null;
        switch (slotId) {
            case "head":
            case "hat":
                return HEAD;
            case "necklace":
            case "choker_trinket":
                return NECK;
            case "back":
            case "cape":
                return BACK;
            case "belt":
                return BELT;
            case "hands":
            case "gloves":
            case "bracelet":
            case "ring":
                return HANDS;
            case "body":
            case "upperwear":
                return BODY;
            case "legs":
            case "pants":
            case "legwear":
                return LEGS;
            case "feet":
            case "shoes":
                return FEET;
            default:
                return null;
        }
    }
}
