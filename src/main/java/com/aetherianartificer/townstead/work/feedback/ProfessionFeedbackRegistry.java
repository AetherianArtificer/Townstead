package com.aetherianartificer.townstead.work.feedback;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, reloadable view of profession feedback. */
public final class ProfessionFeedbackRegistry {
    public record Channel(ResourceLocation profession, long interval, double range,
                          Map<String, ProfessionFeedbackDocument.Rule> rules) {
        @Nullable
        public ProfessionFeedbackDocument.Rule rule(String id) {
            return id == null ? null : rules.get(id);
        }

        public List<ProfessionFeedbackDocument.Rule> periodicRules() {
            return rules.values().stream().filter(ProfessionFeedbackDocument.Rule::periodic)
                    .sorted(Comparator.comparingInt(ProfessionFeedbackDocument.Rule::priority).reversed()
                            .thenComparing(ProfessionFeedbackDocument.Rule::id))
                    .toList();
        }
    }

    private static volatile Map<ResourceLocation, Channel> CHANNELS = Map.of();

    private ProfessionFeedbackRegistry() {}

    static void replaceAll(List<ProfessionFeedbackDocument.Settings> settings,
                           List<ProfessionFeedbackDocument.Rule> rules) {
        Map<ResourceLocation, ProfessionFeedbackDocument.Settings> settingsByProfession = new LinkedHashMap<>();
        settings.stream().sorted(Comparator.comparing(value -> value.profession().toString()))
                .forEach(value -> settingsByProfession.put(value.profession(), value));

        Map<ResourceLocation, Map<String, ProfessionFeedbackDocument.Rule>> rulesByProfession = new LinkedHashMap<>();
        rules.stream().sorted(Comparator.comparing(value -> value.source().toString())).forEach(rule ->
                rulesByProfession.computeIfAbsent(rule.profession(), ignored -> new LinkedHashMap<>())
                        .put(rule.id(), rule));

        Map<ResourceLocation, Channel> compiled = new LinkedHashMap<>();
        java.util.LinkedHashSet<ResourceLocation> professions = new java.util.LinkedHashSet<>();
        professions.addAll(settingsByProfession.keySet());
        professions.addAll(rulesByProfession.keySet());
        for (ResourceLocation profession : professions) {
            ProfessionFeedbackDocument.Settings values = settingsByProfession.get(profession);
            long interval = values == null ? 1200L : values.interval();
            double range = values == null ? 24.0 : values.range();
            Map<String, ProfessionFeedbackDocument.Rule> professionRules =
                    rulesByProfession.getOrDefault(profession, Map.of());
            compiled.put(profession, new Channel(profession, interval, range,
                    java.util.Collections.unmodifiableMap(new LinkedHashMap<>(professionRules))));
        }
        CHANNELS = java.util.Collections.unmodifiableMap(compiled);
    }

    public static List<Channel> all() {
        return List.copyOf(CHANNELS.values());
    }

    @Nullable
    public static Channel byProfession(ResourceLocation profession) {
        return profession == null ? null : CHANNELS.get(profession);
    }

    /** The directory-derived profession gate; authors never have to repeat it as a condition. */
    public static boolean matchesProfession(ResourceLocation profession, VillagerEntityMCA villager) {
        ResourceLocation actual = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().getProfession());
        actual = com.aetherianartificer.townstead.profession.def.ProfessionDefs.canonicalId(actual);
        return profession != null && profession.equals(actual);
    }

    public static boolean matches(Condition condition, VillagerEntityMCA villager) {
        try {
            return condition != null && condition.test(new ConditionContext(villager));
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
