package com.aetherianartificer.townstead.profession.def;

import net.minecraft.resources.ResourceLocation;

/** One ordered MCA clothing identity and the way its headwear interacts with hair. */
public record ClothingChoice(ResourceLocation id, HairPolicy hair) {

    public ClothingChoice {
        if (id == null) throw new IllegalArgumentException("Clothing identity cannot be null");
        if (hair == null) hair = HairPolicy.NORMAL;
    }

    public ClothingChoice(ResourceLocation id) {
        this(id, HairPolicy.NORMAL);
    }

    public enum HairPolicy {
        /** Preserve MCA's ordinary hair rendering. */
        NORMAL,
        /** Hide hair attached to the head, retaining parts of the hairstyle that hang over the body. */
        COVERED,
        /** Hide every part of the hairstyle while this clothing is selected. */
        HIDDEN;

        public static HairPolicy fromString(String value) {
            if (value == null || value.isBlank() || "normal".equalsIgnoreCase(value)) return NORMAL;
            if ("covered".equalsIgnoreCase(value)) return COVERED;
            if ("hidden".equalsIgnoreCase(value)) return HIDDEN;
            throw new IllegalArgumentException("Unknown clothing hair policy '" + value + "'");
        }
    }
}
