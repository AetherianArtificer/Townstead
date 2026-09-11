package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.TownsteadAPI;
import com.aetherianartificer.townstead.api.TownsteadGeneSnapshot;
import com.aetherianartificer.townstead.api.TownsteadGeneVariantSnapshot;
import com.aetherianartificer.townstead.api.TownsteadLifeStageSnapshot;
import com.aetherianartificer.townstead.api.TownsteadRootSnapshot;
import com.aetherianartificer.townstead.api.TownsteadScheduleSnapshot;
import com.aetherianartificer.townstead.api.TownsteadVillagerSnapshot;
import com.aetherianartificer.townstead.api.v1.model.GeneSnapshot;
import com.aetherianartificer.townstead.api.v1.model.NeedLevel;
import com.aetherianartificer.townstead.api.v1.model.NeedsSnapshot;
import com.aetherianartificer.townstead.api.v1.model.RootSnapshot;
import com.aetherianartificer.townstead.api.v1.model.ScheduleSnapshot;
import com.aetherianartificer.townstead.api.v1.model.VillagerSnapshot;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Conversions from the pre-v1 facade's records and live state into v1 snapshots. */
final class ApiSnapshots {
    private ApiSnapshots() {}

    static @Nullable VillagerSnapshot villager(@Nullable Entity entity) {
        if (entity == null) return null;
        TownsteadVillagerSnapshot legacy = TownsteadAPI.entity(entity);
        if (legacy == null) return null;
        VillagerEntityMCA villager = ApiSupport.villager(entity);
        NeedsSnapshot needs = villager != null
                ? needs(TownsteadVillagers.get(villager).needs())
                : emptyNeeds();
        String professionId = legacy.professionId();
        int tier = legacy.professionLevel();
        int xp = legacy.professionXp();
        if (villager == null && entity instanceof Player player) {
            // Players practise careers too; the pre-v1 facade left these zero for them.
            com.aetherianartificer.townstead.profession.career.CareerProfile profile =
                    com.aetherianartificer.townstead.profession.career.PlayerCareers.get(player);
            if (profile != null && profile.primaryVocation() != null) {
                professionId = profile.primaryVocation().toString();
                com.aetherianartificer.townstead.villager.ProfessionXpStore store =
                        com.aetherianartificer.townstead.profession.career.PlayerCareers.xpStore(player);
                tier = com.aetherianartificer.townstead.villager.ProfessionProgress.getTier(store, profile.primaryVocation());
                xp = com.aetherianartificer.townstead.villager.ProfessionProgress.getXp(store, profile.primaryVocation());
            }
        }
        return new VillagerSnapshot(
                entity.getUUID(),
                legacy.name(),
                legacy.entityType(),
                entity instanceof Player,
                ApiSupport.homeOf(entity),
                legacy.rootId(),
                legacy.personalityId(),
                legacy.lifeStage(),
                legacy.biologicalAgeDays(),
                legacy.apparentAgeYears(),
                legacy.immortal(),
                legacy.ageless(),
                legacy.senior(),
                legacy.fertility(),
                professionId,
                legacy.professionPathId(),
                tier,
                xp,
                needs,
                schedule(legacy.schedule()),
                legacy.carriedVariants(),
                legacy.expressedAlleles(),
                legacy.heritage());
    }

    static NeedsSnapshot needs(TownsteadVillager.Needs needs) {
        Map<String, NeedLevel> levels = NeedScales.levels(needs);
        return new NeedsSnapshot(
                needs.hunger(),
                needs.saturation(),
                needs.hungerExhaustion(),
                needs.thirst(),
                needs.quenched(),
                needs.thirstExhaustion(),
                levels.get(NeedsSnapshot.ENERGY).value(),
                needs.collapsed(),
                NeedScales.thirstEnabled(),
                needs.bodyTempTenths(),
                needs.ambientTenths(),
                levels);
    }

    static NeedsSnapshot emptyNeeds() {
        return new NeedsSnapshot(0, 0f, 0f, 0, 0, 0f, 0, false, false, Integer.MIN_VALUE, 0, Map.of());
    }

    static ScheduleSnapshot schedule(TownsteadScheduleSnapshot s) {
        if (s == null) {
            return new ScheduleSnapshot("", "", false, false, 0, 6, 0, "", "", "", List.of(), List.of());
        }
        return new ScheduleSnapshot(s.mode(), s.templateId(), s.customShifts(), s.nonDefaultCustomShifts(),
                s.currentTickHour(), s.currentDisplayHour(), s.currentShiftOrdinal(), s.currentActivity(),
                s.plannedActivity(), s.currentTemplateId(), s.shifts(), s.weekDayTemplates());
    }

    static @Nullable RootSnapshot root(@Nullable TownsteadRootSnapshot r) {
        if (r == null) return null;
        List<RootSnapshot.LifeStageInfo> stages = new ArrayList<>();
        for (TownsteadLifeStageSnapshot stage : r.lifeStages()) {
            stages.add(new RootSnapshot.LifeStageInfo(stage.id(), stage.label(), stage.days(), stage.scale(),
                    stage.presentsAs(), stage.narrativeStart(), stage.narrativeEnd()));
        }
        return new RootSnapshot(r.id(), r.displayName(), r.species(), r.ancestry(), r.lineage(),
                r.effectiveSpecies(), r.defaultGenes(), stages);
    }

    static @Nullable GeneSnapshot gene(@Nullable TownsteadGeneSnapshot g) {
        if (g == null) return null;
        List<GeneSnapshot.VariantInfo> variants = new ArrayList<>();
        for (TownsteadGeneVariantSnapshot v : g.variants()) {
            variants.add(new GeneSnapshot.VariantInfo(v.id(), v.displayName(), v.weight(), v.type()));
        }
        return new GeneSnapshot(g.id(), g.displayName(), g.description(), g.category(), g.dominance(),
                g.locus(), g.weight(), g.displayMode(), variants);
    }
}
