package com.aetherianartificer.townstead.chronicle.emit;

import java.util.List;
import java.util.Map;

/**
 * Every semantic key the game reports to the chronicle. These are a contract in
 * two directions: templates bind to them, and Careers counts them
 * ({@code pheno:chronicle_count}), so a typo in a string literal silently breaks
 * both. Naming them here also makes coverage answerable — which keys the game
 * can report and no template listens for.
 *
 * <p>A key is a semantic verb, not a task: several tasks may report
 * {@code butchered}, and one task may report different keys depending on what it
 * did. New work reuses an existing verb wherever the story is the same.</p>
 */
public final class ChronicleTapKeys {

    // work
    public static final String COOKED = "townstead:cooked";
    public static final String BREWED = "townstead:brewed";
    public static final String PRODUCED = "townstead:produced";
    public static final String PIZZA = "townstead:pizza";
    public static final String HARVESTED = "townstead:harvested";
    public static final String PLANTED = "townstead:planted";
    public static final String TILLED = "townstead:tilled";
    public static final String GROOMED = "townstead:groomed";
    public static final String IRRIGATED = "townstead:irrigated";
    public static final String FARMED = "townstead:farmed";
    public static final String TENDED = "townstead:tended";
    public static final String SLAUGHTERED = "townstead:slaughtered";
    public static final String BUTCHERED = "townstead:butchered";
    public static final String CLEANED = "townstead:cleaned";
    public static final String TOOK_UP_WORK = "townstead:took_up_work";
    public static final String MASTERED = "townstead:mastered";
    public static final String LEARNED_CRAFT = "townstead:learned_craft";

    // survival
    public static final String STARVING = "townstead:starving";
    public static final String CURED = "townstead:cured";

    // social
    public static final String FRIENDSHIP = "townstead:friendship";
    public static final String ARGUMENT = "townstead:argument";

    // lifecycle
    public static final String BIRTH = "townstead:birth";
    public static final String DEATH = "townstead:death";
    public static final String MARRIAGE = "townstead:marriage";

    // taboo
    public static final String ATE_SAPIENT_FLESH = "townstead:ate_sapient_flesh";
    public static final String BECAME_CANNIBAL = "townstead:became_cannibal";

    /** Every key the game can report, by trigger type. */
    public static final Map<String, List<String>> BY_TYPE = Map.of(
            "work", List.of(COOKED, BREWED, PRODUCED, PIZZA, HARVESTED, PLANTED, TILLED,
                    GROOMED, IRRIGATED, FARMED, TENDED, SLAUGHTERED, BUTCHERED, CLEANED,
                    TOOK_UP_WORK, MASTERED, LEARNED_CRAFT),
            "survival", List.of(STARVING, CURED),
            "social", List.of(FRIENDSHIP, ARGUMENT),
            "lifecycle", List.of(BIRTH, DEATH, MARRIAGE),
            "taboo", List.of(ATE_SAPIENT_FLESH, BECAME_CANNIBAL));

    private ChronicleTapKeys() {}
}
