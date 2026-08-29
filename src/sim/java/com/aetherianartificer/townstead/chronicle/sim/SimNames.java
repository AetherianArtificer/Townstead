package com.aetherianartificer.townstead.chronicle.sim;

import net.minecraft.util.RandomSource;

/**
 * Stand-in for MCA's name pool. Drawn from a dedicated RNG so naming never
 * shifts the generation stream (see {@code ChronicleWorld.fabricateName}).
 */
public final class SimNames {

    private static final String[] FEMININE = {
            "Maren", "Sena", "Bryn", "Ilsa", "Rowan", "Tamsin", "Odile", "Wren",
            "Petra", "Alma", "Cordis", "Verity"
    };
    private static final String[] MASCULINE = {
            "Til", "Ivo", "Corwin", "Ansel", "Baird", "Hollis", "Emmet", "Garr",
            "Osric", "Fen", "Lucan", "Mattis"
    };
    private static final String[] FAMILY = {
            "Oakhollow", "Ashdown", "Colefield", "Marsh", "Reed", "Bellrow",
            "Thistlewaite", "Grange", "Fernly", "Stonebrook"
    };

    private SimNames() {}

    public static String pick(boolean feminine, RandomSource rng) {
        String[] given = feminine ? FEMININE : MASCULINE;
        return given[rng.nextInt(given.length)] + " " + FAMILY[rng.nextInt(FAMILY.length)];
    }
}
