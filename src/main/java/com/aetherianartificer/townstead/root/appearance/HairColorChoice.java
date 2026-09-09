package com.aetherianartificer.townstead.root.appearance;

/** One exact authored MCA hair-dye color (opaque RGB) and its population roll weight. */
public record HairColorChoice(int rgb, int weight) {
    public HairColorChoice {
        rgb &= 0xFFFFFF;
        weight = Math.max(0, weight);
    }

    /** MCA reserves opaque black as its "no dye" sentinel, so exact black uses an indistinguishable near-black. */
    public int argb() { return rgb == 0 ? 0xFF000001 : 0xFF000000 | rgb; }
}
