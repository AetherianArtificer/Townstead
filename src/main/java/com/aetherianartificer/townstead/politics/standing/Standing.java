package com.aetherianartificer.townstead.politics.standing;

/**
 * How much a person's word counts in one settlement, from three sources on one scale: the
 * residents' hearts (MCA), the deeds they did there (Townstead), and their MCA: Reputation score.
 */
public record Standing(int hearts, int deeds, int reputation) {
    public static final Standing NONE = new Standing(0, 0, 0);

    public int total() {
        return hearts + deeds + reputation;
    }
}
