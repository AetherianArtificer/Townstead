package com.aetherianartificer.townstead.api;

import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.calendar.WorldCalendarSavedData;
import com.aetherianartificer.townstead.origin.LifeCycle;
import com.aetherianartificer.townstead.origin.Origin;
import com.aetherianartificer.townstead.origin.OriginRegistry;
import com.aetherianartificer.townstead.origin.PlayerOrigin;
import com.aetherianartificer.townstead.origin.gene.Gene;
import com.aetherianartificer.townstead.origin.gene.GeneRegistry;
import com.aetherianartificer.townstead.origin.gene.InheritedGene;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.template.ShiftTemplate;
import com.aetherianartificer.townstead.shift.template.ShiftTemplateRegistry;
import com.aetherianartificer.townstead.villager.ProfessionXp;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable read-only integration facade for Townstead state. */
public final class TownsteadAPI {
    private TownsteadAPI() {}

    public static TownsteadVillagerSnapshot entity(Entity entity) {
        if (entity instanceof VillagerEntityMCA villager) return villager(villager);
        if (entity instanceof Player player) return player(player);
        return null;
    }

    public static TownsteadVillagerSnapshot villager(VillagerEntityMCA villager) {
        TownsteadVillager state = TownsteadVillagers.get(villager);
        TownsteadVillager.Life life = state.life();
        TownsteadVillager.Needs needs = state.needs();
        TownsteadVillager.ScheduleState schedule = state.schedule();
        ResourceLocation professionKey = BuiltInRegistries.VILLAGER_PROFESSION.getKey(
                villager.getVillagerData().getProfession());
        String professionId = professionKey == null ? "" : professionKey.toString();
        ProfessionXp xp = state.professionMemory().professionXp(professionId);
        MinecraftServer server = villager.getServer();
        long ageDays = 0L;
        int ageYears = 0;
        if (server != null && life.hasBirth()) {
            ageDays = Math.max(0L, TownsteadCalendar.lifeDay(server) - life.birthWorldDay());
            ageYears = TownsteadCalendar.ageYears(server, villager);
        }
        return new TownsteadVillagerSnapshot(
                villager.getUUID().toString(),
                villager.getName().getString(),
                villager.getType().toString(),
                life.originId(),
                life.currentStageId(),
                ageDays,
                ageYears,
                life.immortal(),
                life.ageless(),
                life.isSenior(),
                life.personalityId(),
                professionId,
                xp.tier(),
                xp.xp(),
                life.fertility(),
                new TownsteadAgeSnapshot(
                        life.currentStageId(),
                        ageDays,
                        ageYears,
                        life.immortal(),
                        life.ageless(),
                        life.isSenior()),
                schedule(villager, schedule),
                new TownsteadNeedsSnapshot(
                        needs.hunger(),
                        needs.saturation(),
                        needs.hungerExhaustion(),
                        needs.thirst(),
                        needs.quenched(),
                        needs.thirstExhaustion(),
                        needs.fatigue(),
                        needs.collapsed(),
                        needs.gated()),
                mapStringString(life.carriedVariants()),
                List.copyOf(life.expressedAlleles()),
                mapResourceFloat(life.heritage().fractions())
        );
    }

    public static TownsteadVillagerSnapshot player(Player player) {
        String originId = PlayerOrigin.getOriginId(player);
        return new TownsteadVillagerSnapshot(
                player.getUUID().toString(),
                player.getName().getString(),
                player.getType().toString(),
                originId,
                "",
                0L,
                0,
                false,
                false,
                false,
                "",
                "",
                0,
                0,
                0f,
                new TownsteadAgeSnapshot("", 0L, 0, false, false, false),
                new TownsteadScheduleSnapshot("", "", false, false, 0, 6, 0, "", "", "", List.of(), List.of()),
                new TownsteadNeedsSnapshot(0, 0f, 0f, 0, 0, 0f, 0, false, false),
                Map.of(),
                List.of(),
                Map.of()
        );
    }

    public static TownsteadCalendarSnapshot calendar(MinecraftServer server) {
        WorldCalendarSavedData data = WorldCalendarSavedData.get(server);
        CalendarProfile profile = TownsteadCalendar.activeProfile(server);
        CalendarDate today = TownsteadCalendar.today(server);
        return new TownsteadCalendarSnapshot(
                profile == null ? "" : profile.id().toString(),
                data.worldDayCounter(),
                data.epochYearOffset(),
                TownsteadCalendar.activeTimeMode(server),
                today.year(),
                today.monthIndex(),
                today.dayOfMonth(),
                today.dayOfYear(),
                today.dayOfWeek(),
                today.season() == null ? "" : today.season().name().toLowerCase(java.util.Locale.ROOT)
        );
    }

    public static TownsteadOriginSnapshot origin(ResourceLocation id) {
        Origin origin = OriginRegistry.byId(id);
        if (origin == null) return null;
        List<String> genes = new ArrayList<>();
        for (InheritedGene inherited : OriginRegistry.effectiveInheritedGenes(id)) {
            genes.add(inherited.geneId().toString());
        }
        LifeCycle cycle = OriginRegistry.effectiveLifeCycle(id);
        List<TownsteadLifeStageSnapshot> stages = new ArrayList<>();
        if (cycle != null) {
            for (int i = 0; i < cycle.size(); i++) {
                var stage = cycle.stageAt(i);
                stages.add(new TownsteadLifeStageSnapshot(
                        stage.id(),
                        stage.label().getString(),
                        stage.days(),
                        stage.scale(),
                        stage.presentsAs().name().toLowerCase(java.util.Locale.ROOT),
                        stage.narrativeStart(),
                        stage.narrativeEnd()));
            }
        }
        ResourceLocation effectiveSpecies = OriginRegistry.effectiveSpecies(id);
        return new TownsteadOriginSnapshot(
                origin.id().toString(),
                origin.displayName().getString(),
                string(origin.species()),
                string(origin.ancestry()),
                string(origin.lineage()),
                string(effectiveSpecies),
                List.copyOf(genes),
                List.copyOf(stages)
        );
    }

    public static TownsteadGeneSnapshot gene(ResourceLocation id) {
        Gene gene = GeneRegistry.byId(id);
        if (gene == null) return null;
        List<TownsteadGeneVariantSnapshot> variants = new ArrayList<>();
        for (var variant : gene.variants()) {
            variants.add(new TownsteadGeneVariantSnapshot(
                    variant.id(),
                    variant.displayName().getString(),
                    variant.weight(),
                    variant.instance().getClass().getSimpleName()));
        }
        return new TownsteadGeneSnapshot(
                gene.id().toString(),
                gene.displayName().getString(),
                gene.description() == null ? "" : gene.description().getString(),
                gene.category(),
                gene.dominance().name().toLowerCase(java.util.Locale.ROOT),
                string(gene.locus()),
                gene.weight(),
                gene.display().kind().name().toLowerCase(java.util.Locale.ROOT),
                List.copyOf(variants)
        );
    }

    private static String string(ResourceLocation id) {
        return id == null ? "" : id.toString();
    }

    private static Map<String, String> mapStringString(Map<String, String> in) {
        return in == null || in.isEmpty() ? Map.of() : Map.copyOf(new LinkedHashMap<>(in));
    }

    private static Map<String, Float> mapResourceFloat(Map<ResourceLocation, Float> in) {
        if (in == null || in.isEmpty()) return Map.of();
        Map<String, Float> out = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Float> entry : in.entrySet()) {
            out.put(entry.getKey().toString(), entry.getValue());
        }
        return Map.copyOf(out);
    }

    private static List<Integer> intList(int[] values) {
        if (values == null || values.length == 0) return List.of();
        List<Integer> out = new ArrayList<>(values.length);
        for (int value : values) out.add(value);
        return List.copyOf(out);
    }

    private static TownsteadScheduleSnapshot schedule(VillagerEntityMCA villager, TownsteadVillager.ScheduleState schedule) {
        long dayTime = villager.level() == null ? 0L : Math.floorMod(villager.level().getDayTime(), 24000L);
        int tickHour = (int) (dayTime / ShiftData.TICKS_PER_HOUR);
        ResolvedSchedule resolved = resolveTodaySchedule(villager, schedule);
        int[] planned = resolved.shifts() == null ? schedule.copyShifts() : resolved.shifts();
        int plannedOrdinal = ordinalAt(planned, tickHour);
        Activity current = villager.getBrain() == null
                ? ShiftData.ORDINAL_TO_ACTIVITY[plannedOrdinal]
                : villager.getBrain().getSchedule().getActivityAt((int) dayTime);
        int currentOrdinal = ShiftData.activityToOrdinal(current);
        return new TownsteadScheduleSnapshot(
                schedule.mode(),
                schedule.templateId(),
                schedule.hasCustomShifts(),
                schedule.hasNonDefaultCustomShifts(),
                tickHour,
                ShiftData.toDisplayHour(tickHour),
                currentOrdinal,
                shiftName(currentOrdinal),
                shiftName(plannedOrdinal),
                resolved.templateId(),
                intList(schedule.copyShifts()),
                schedule.weekDayTemplates()
        );
    }

    private static ResolvedSchedule resolveTodaySchedule(VillagerEntityMCA villager, TownsteadVillager.ScheduleState schedule) {
        if (ShiftData.MODE_WEEKLY.equals(schedule.mode())) {
            MinecraftServer server = villager.getServer();
            int dayOfWeek = 0;
            if (server != null) dayOfWeek = Math.max(0, TownsteadCalendar.today(server).dayOfWeek());
            List<String> weekDays = schedule.weekDayTemplates();
            String templateId = dayOfWeek >= 0 && dayOfWeek < weekDays.size() ? weekDays.get(dayOfWeek) : "";
            if (server != null && templateId != null && !templateId.isEmpty()) {
                ResourceLocation id = ResourceLocation.tryParse(templateId);
                if (id != null) {
                    java.util.Optional<ShiftTemplate> template = ShiftTemplateRegistry.resolve(server, id);
                    if (template.isPresent()) return new ResolvedSchedule(template.get().copyShifts(), templateId);
                }
            }
            return new ResolvedSchedule(schedule.hasCustomShifts() ? schedule.copyShifts() : null, "");
        }
        return new ResolvedSchedule(schedule.hasCustomShifts() ? schedule.copyShifts() : null, schedule.templateId());
    }

    private static int ordinalAt(int[] shifts, int tickHour) {
        if (shifts == null || shifts.length == 0) return ShiftData.ORD_IDLE;
        int ord = shifts[Math.floorMod(tickHour, shifts.length)];
        return ord >= 0 && ord < ShiftData.ORDINAL_TO_ACTIVITY.length ? ord : ShiftData.ORD_IDLE;
    }

    private static String shiftName(int ordinal) {
        return switch (ordinal) {
            case ShiftData.ORD_WORK -> "work";
            case ShiftData.ORD_MEET -> "meet";
            case ShiftData.ORD_REST -> "rest";
            default -> "idle";
        };
    }

    private record ResolvedSchedule(int[] shifts, String templateId) {}
}
