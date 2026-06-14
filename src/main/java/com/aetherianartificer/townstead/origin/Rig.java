package com.aetherianartificer.townstead.origin;

/**
 * A species' rig: the base model it renders as, and a uniform render {@code scale}. {@code base}
 * resolves to the MCA villager model ({@code mca:villager}), a registered vanilla/modded entity
 * model ({@code minecraft:spider}), or a custom {@code .geo.json} shipped in the data pack. The
 * renderer interprets the reference; composition (grafting parts of several rigs) is reserved for
 * a later pass. {@code scale} sizes the rendered model so a pack can make tiny or giant species
 * (a stopgap until the full species {@code size} envelope lands).
 */
public record Rig(String base, float scale) {

    public static final Rig VILLAGER = new Rig("mca:villager", 1.0f);

    public Rig {
        base = base == null || base.isBlank() ? "mca:villager" : base;
        if (scale <= 0f) scale = 1.0f;
    }
}
