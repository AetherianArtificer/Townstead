package com.aetherianartificer.townstead.chronicle.pregen;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

/** Competence rates by profession id, replaced wholesale on data-pack load. */
public final class CompetenceDefs {

    /** Rates that apply whatever the trade, declared with an empty profession. */
    private static volatile Map<String, Integer> COMMON = Map.of();
    private static volatile Map<String, Map<String, Integer>> BY_PROFESSION = Map.of();

    private CompetenceDefs() {}

    public static void replaceAll(Map<ResourceLocation, Competence> entries) {
        Map<String, Map<String, Integer>> byProfession = new LinkedHashMap<>();
        Map<String, Integer> common = new LinkedHashMap<>();
        for (Competence competence : entries.values()) {
            if (competence.profession().isEmpty()) {
                common.putAll(competence.perYear());
            } else {
                byProfession.merge(competence.profession(),
                        new LinkedHashMap<>(competence.perYear()), (a, b) -> {
                            a.putAll(b);
                            return a;
                        });
            }
        }
        COMMON = Map.copyOf(common);
        BY_PROFESSION = Map.copyOf(byProfession);
    }

    /** Per-year counter rates for a trade, including the ones everyone accrues. */
    public static Map<String, Integer> perYear(String professionId) {
        Map<String, Integer> trade = BY_PROFESSION.get(professionId);
        if (trade == null || trade.isEmpty()) return COMMON;
        if (COMMON.isEmpty()) return trade;
        Map<String, Integer> merged = new LinkedHashMap<>(COMMON);
        merged.putAll(trade);
        return merged;
    }

    public static boolean isEmpty() {
        return COMMON.isEmpty() && BY_PROFESSION.isEmpty();
    }
}
