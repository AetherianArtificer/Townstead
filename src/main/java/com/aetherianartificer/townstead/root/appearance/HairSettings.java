package com.aetherianartificer.townstead.root.appearance;

import java.util.List;

/** Fully resolved hair availability and allowed genetic-colormap regions for one individual. */
public record HairSettings(boolean enabled, List<HairColorRange> colorRanges,
        List<HairColorChoice> colors, List<HairGradient> gradients) {
    public HairSettings {
        colorRanges = colorRanges == null ? List.of() : List.copyOf(colorRanges);
        colors = colors == null ? List.of() : List.copyOf(colors);
        gradients = gradients == null ? List.of() : List.copyOf(gradients);
    }

    public static final HairSettings DEFAULT = new HairSettings(true, List.of(), List.of(), List.of());
}
