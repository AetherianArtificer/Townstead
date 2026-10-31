package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.CalendarRolloverEvent;
import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.social.RelationshipLedger;
import com.aetherianartificer.townstead.social.RelationshipQualities;
import com.aetherianartificer.townstead.spirit.SpiritTotals;
import com.aetherianartificer.townstead.spirit.VillageSpiritAggregator;
import com.aetherianartificer.townstead.switchboard.Systems;
import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Moves each villager's culture blend a little every day toward what surrounds them: their home
 * village, read through Community Spirit and its government; the people they are close to; and, for
 * children, their parents. Adults take years to shift most of their blend; children far less.
 * A villager who was unloaded catches up on the days they missed when the next pass finds them.
 */
public final class CultureDrift {
    /** Years for an adult to move about two thirds of the way to their surroundings. */
    private static final double ADULT_YEARS = 3.0;
    /** Children drift this many times faster than adults. */
    private static final double CHILD_FACTOR = 4.0;
    /** How far a village's spirit and government pull its people toward the matching subcultures. */
    private static final double SPIRIT_PULL = 0.5;
    /** Extra pull toward the subcultures whose towns found this village's form of government. */
    private static final double FORM_PULL = 1.0;
    /** A share below this fades out, so blends stay readable. */
    private static final float FADE = 0.02F;
    /** Affection at which a friend's culture rubs off. */
    private static final double CLOSE_FRIEND = 40.0;
    private static final int MAX_CATCH_UP_DAYS = 365;
    private static boolean registered;

    private CultureDrift() {}

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TownsteadApiV1.get().events().subscribe(CalendarRolloverEvent.class, e -> {
            if (e.kind() == CalendarRolloverEvent.Kind.DAY) daily(e.server());
        });
    }

    static void daily(MinecraftServer server) {
        if (!Systems.on(Systems.CULTURES)) return;
        long today = TownsteadCalendar.worldDay(server);
        int daysPerYear = Math.max(1, TownsteadCalendar.activeProfile(server).daysPerYear());
        double adultRate = 1.0 / (ADULT_YEARS * daysPerYear);
        RelationshipLedger ledger = ChronicleSavedData.get(server).relationships();
        PoliticalSavedData politics = PoliticalSavedData.get(server);
        for (ServerLevel level : server.getAllLevels()) {
            for (Village village : VillageManager.get(level)) {
                List<VillagerEntityMCA> residents = new ArrayList<>();
                village.getResidentsUUIDs().forEach(id -> {
                    if (id != null && level.getEntity(id) instanceof VillagerEntityMCA villager) residents.add(villager);
                });
                if (residents.isEmpty()) continue;
                Map<String, Float> home = homePull(level, village, residents, politics);
                for (VillagerEntityMCA villager : residents) {
                    drift(level, villager, home, ledger, today, adultRate);
                }
            }
        }
    }

    private static void drift(ServerLevel level, VillagerEntityMCA villager, Map<String, Float> home,
                              RelationshipLedger ledger, long today, double adultRate) {
        TownsteadVillager.Life life = TownsteadVillagers.get(villager).life();
        Map<String, Float> blend = life.cultureBlend();
        long last = life.cultureDriftDay();
        if (blend.isEmpty() || last < 0 || today <= last) {
            if (last != today) life.setCultureDriftDay(today);
            return;
        }
        int days = (int) Math.min(MAX_CATCH_UP_DAYS, today - last);
        boolean child = villager.isBaby();
        Map<String, Float> close = average(closeTo(level, villager, ledger, today));
        Map<String, Float> parents = child ? average(parentsOf(villager)) : Map.of();
        Map<String, Float> target = new HashMap<>();
        double weight = 0;
        weight += add(target, home, child ? 0.35 : 0.65);
        weight += add(target, close, child ? 0.15 : 0.35);
        weight += add(target, parents, child ? 0.5 : 0.0);
        if (weight > 0) {
            double rate = child ? adultRate * CHILD_FACTOR : adultRate;
            double keep = Math.pow(1.0 - Math.min(1.0, rate), days);
            Map<String, Float> next = new LinkedHashMap<>();
            for (String key : union(blend, target)) {
                double now = blend.getOrDefault(key, 0F);
                double aim = target.getOrDefault(key, 0F) / weight;
                float value = (float) (aim + (now - aim) * keep);
                if (value >= FADE) next.put(key, value);
            }
            if (!next.isEmpty()) life.setCultureBlend(next);
        }
        life.setCultureDriftDay(today);
        TownsteadVillagers.flush(villager);
    }

    /**
     * What living in this village pulls toward: its people's combined blend, and within each culture,
     * the subcultures its Community Spirit and its government favor. A culture with no subculture
     * listing the village's strong axes keeps its people as they are.
     */
    public static Map<String, Float> homePull(ServerLevel level, Village village, List<VillagerEntityMCA> residents,
                                       PoliticalSavedData politics) {
        Map<String, Float> composition = new HashMap<>();
        for (VillagerEntityMCA resident : residents) {
            TownsteadVillagers.get(resident).life().cultureBlend().forEach((k, v) -> composition.merge(k, v, Float::sum));
        }
        if (composition.isEmpty()) return Map.of();
        SpiritTotals spirit = VillageSpiritAggregator.totalsFor(village);
        SettlementFoundingRecord founding = politics.founding(new SettlementRef(level.dimension().location(), village.getId()));
        ResourceLocation profile = founding == null ? null : founding.profile();

        Map<String, Map<String, Float>> families = new HashMap<>();
        composition.forEach((culture, share) -> families.computeIfAbsent(Cultures.rootOf(culture), r -> new HashMap<>()).merge(culture, share, Float::sum));
        Map<String, Float> out = new HashMap<>();
        for (Map.Entry<String, Map<String, Float>> family : families.entrySet()) {
            Map<String, Float> members = family.getValue();
            float mass = 0;
            for (float v : members.values()) mass += v;
            Map<String, Double> pulls = new HashMap<>();
            double pullTotal = 0;
            for (Culture sub : Cultures.subculturesOf(ResourceLocation.tryParse(family.getKey()))) {
                double pull = Subcultures.spiritFit(sub, spirit);
                if (profile != null && Subcultures.founds(sub, profile)) pull += FORM_PULL;
                if (pull > 0) {
                    pulls.put(sub.id().toString(), pull);
                    pullTotal += pull;
                }
            }
            double alpha = pullTotal > 0 ? SPIRIT_PULL : 0;
            for (Map.Entry<String, Float> member : members.entrySet()) {
                out.merge(member.getKey(), (float) ((1 - alpha) * member.getValue()), Float::sum);
            }
            if (alpha > 0) {
                for (Map.Entry<String, Double> pull : pulls.entrySet()) {
                    out.merge(pull.getKey(), (float) (alpha * mass * pull.getValue() / pullTotal), Float::sum);
                }
            }
        }
        return out;
    }

    private static List<Map<String, Float>> closeTo(ServerLevel level, VillagerEntityMCA villager,
                                                    RelationshipLedger ledger, long today) {
        List<Map<String, Float>> out = new ArrayList<>();
        try {
            Optional<UUID> partner = villager.getRelationships().getPartnerUUID();
            partner.ifPresent(id -> blendOf(level, id).ifPresent(out::add));
        } catch (Throwable ignored) {
        }
        for (UUID friend : ledger.targetsOf(villager.getUUID())) {
            if (ledger.value(villager.getUUID(), friend, RelationshipQualities.AFFECTION, today) < CLOSE_FRIEND) continue;
            blendOf(level, friend).ifPresent(out::add);
        }
        return out;
    }

    private static List<Map<String, Float>> parentsOf(VillagerEntityMCA villager) {
        List<Map<String, Float>> out = new ArrayList<>();
        try {
            villager.getRelationships().getParents().forEach(parent -> {
                if (parent instanceof VillagerEntityMCA person) {
                    Map<String, Float> blend = TownsteadVillagers.get(person).life().cultureBlend();
                    if (!blend.isEmpty()) out.add(blend);
                }
            });
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static Optional<Map<String, Float>> blendOf(ServerLevel level, UUID id) {
        Entity entity = level.getEntity(id);
        if (!(entity instanceof VillagerEntityMCA person)) return Optional.empty();
        Map<String, Float> blend = TownsteadVillagers.get(person).life().cultureBlend();
        return blend.isEmpty() ? Optional.empty() : Optional.of(blend);
    }

    private static Map<String, Float> average(List<Map<String, Float>> blends) {
        if (blends.isEmpty()) return Map.of();
        Map<String, Float> out = new HashMap<>();
        for (Map<String, Float> blend : blends) blend.forEach((k, v) -> out.merge(k, v / blends.size(), Float::sum));
        return out;
    }

    /** Adds {@code source}, normalized and scaled by {@code weight}; returns the weight it added. */
    private static double add(Map<String, Float> target, Map<String, Float> source, double weight) {
        if (source.isEmpty() || weight <= 0) return 0;
        float total = 0;
        for (float v : source.values()) total += v;
        if (total <= 0) return 0;
        for (Map.Entry<String, Float> e : source.entrySet()) {
            target.merge(e.getKey(), (float) (weight * e.getValue() / total), Float::sum);
        }
        return weight;
    }

    private static List<String> union(Map<String, Float> a, Map<String, Float> b) {
        List<String> keys = new ArrayList<>(a.keySet());
        for (String key : b.keySet()) if (!a.containsKey(key)) keys.add(key);
        return keys;
    }
}
