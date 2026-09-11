package com.aetherianartificer.townstead.root.appearance;

import net.minecraft.network.chat.Component;

/**
 * One exact authored MCA hair-dye color (opaque RGB), its population roll weight, and an
 * optional display name: {@code name} is the server-resolved string, {@code nameKey} its
 * translate key (empty for a literal), so the client can honour a resource-pack override
 * while falling back to the synced text. Shown as the editor swatch's tooltip.
 */
public record HairColorChoice(int rgb, int weight, String name, String nameKey) {
    public HairColorChoice {
        rgb &= 0xFFFFFF;
        weight = Math.max(0, weight);
        name = name == null ? "" : name;
        nameKey = nameKey == null ? "" : nameKey;
    }

    public HairColorChoice(int rgb, int weight) {
        this(rgb, weight, "", "");
    }

    public HairColorChoice(int rgb, int weight, String name) {
        this(rgb, weight, name, "");
    }

    /** MCA reserves opaque black as its "no dye" sentinel, so exact black uses an indistinguishable near-black. */
    public int argb() { return rgb == 0 ? 0xFF000001 : 0xFF000000 | rgb; }

    /** The display name for the editor, or null when the pack gave none. */
    public Component displayName() {
        if (name.isEmpty() && nameKey.isEmpty()) return null;
        return nameKey.isEmpty() ? Component.literal(name) : Component.translatableWithFallback(nameKey, name);
    }
}
