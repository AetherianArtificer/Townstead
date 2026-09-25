package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.calendar.TownsteadCalendar;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;

import java.util.*;

/**
 * Hostile mobs that villagers saw near their village in the last few days. Villagers warn each other
 * about them. Kept in memory only: a sighting is news for a day or two.
 */
public final class MobSightings {
    private MobSightings() {}

    public record Sighting(ResourceLocation type, String name, UUID witness, String witnessName, long day, long gameTime) {}

    static final int RADIUS = 24, MAX_PER_VILLAGE = 8;
    private static final Map<Integer, Deque<Sighting>> BY_VILLAGE = new HashMap<>();

    /** Records the nearest hostile mob this villager can see, at most one sighting of a type per village per day. */
    public static synchronized void observe(ServerLevel level, VillagerEntityMCA villager) {
        List<Monster> near = level.getEntitiesOfClass(Monster.class, villager.getBoundingBox().inflate(RADIUS),
                mob -> mob.isAlive() && villager.hasLineOfSight(mob));
        if (near.isEmpty()) return;
        Monster mob = near.stream().min(Comparator.comparingDouble(villager::distanceToSqr)).orElseThrow();
        Integer village = village(level, villager);
        if (village == null) return;
        long day = TownsteadCalendar.worldDay(level.getServer());
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        Deque<Sighting> list = BY_VILLAGE.computeIfAbsent(village, k -> new ArrayDeque<>());
        if (list.stream().anyMatch(s -> s.type().equals(type) && s.day() == day)) return;
        list.addFirst(new Sighting(type, mob.getType().getDescription().getString(), villager.getUUID(),
                villager.getName().getString(), day, level.getGameTime()));
        while (list.size() > MAX_PER_VILLAGE) list.removeLast();
    }

    /** Sightings in the speaker's village from the last {@code days} days, newest first. */
    public static synchronized List<Sighting> recent(ServerLevel level, Entity speaker, int days) {
        Integer village = village(level, speaker);
        if (village == null) return List.of();
        long today = TownsteadCalendar.worldDay(level.getServer());
        return BY_VILLAGE.getOrDefault(village, new ArrayDeque<>()).stream().filter(s -> today - s.day() <= days).toList();
    }

    public static synchronized void clear() { BY_VILLAGE.clear(); }

    private static Integer village(ServerLevel level, Entity entity) {
        return VillageManager.get(level).findNearestVillage(entity.blockPosition(), Village.MERGE_MARGIN).map(Village::getId).orElse(null);
    }
}
