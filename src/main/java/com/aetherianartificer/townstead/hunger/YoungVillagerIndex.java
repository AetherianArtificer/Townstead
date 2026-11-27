package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.thirst.ThirstData;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The young villagers of each level, for caregivers to search instead of every villager in range.
 * Who is young is rebuilt about once a second, who needs care once per tick. A caregiver that feeds
 * someone can change the answer within a tick, so callers still check each villager's own need.
 */
public final class YoungVillagerIndex {
    private static final long YOUNG_REFRESH_TICKS = 20L;
    private static final EntityTypeTest<Entity, VillagerEntityMCA> VILLAGERS = EntityTypeTest.forClass(VillagerEntityMCA.class);
    private static final Predicate<VillagerEntityMCA> HUNGRY = villager ->
            TownsteadVillagers.get(villager).needs().hunger() < HungerData.ADEQUATE_THRESHOLD;
    private static final Predicate<VillagerEntityMCA> THIRSTY = villager ->
            TownsteadVillagers.get(villager).needs().thirst() < ThirstData.ADEQUATE_THRESHOLD;
    private static final Map<ServerLevel, Index> INDEXES = new HashMap<>();

    private YoungVillagerIndex() {}

    public static List<VillagerEntityMCA> hungry(ServerLevel level) {
        Index index = indexOf(level);
        return index.hungry.of(index.young, HUNGRY, level.getGameTime());
    }

    public static List<VillagerEntityMCA> thirsty(ServerLevel level) {
        Index index = indexOf(level);
        return index.thirsty.of(index.young, THIRSTY, level.getGameTime());
    }

    /** Lets go of the levels and villagers held here when their server stops. */
    public static void clear() {
        INDEXES.clear();
    }

    public static boolean isYoung(VillagerEntityMCA villager) {
        AgeState ageState = AgeState.byCurrentAge(villager.getAge());
        return ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD;
    }

    private static Index indexOf(ServerLevel level) {
        long now = level.getGameTime();
        Index index = INDEXES.computeIfAbsent(level, ignored -> new Index());
        if (index.youngBuiltAt < 0L || now < index.youngBuiltAt || now - index.youngBuiltAt >= YOUNG_REFRESH_TICKS) {
            index.young = new ArrayList<>(level.getEntities(VILLAGERS, YoungVillagerIndex::isYoung));
            index.youngBuiltAt = now;
        }
        return index;
    }

    private static final class Index {
        long youngBuiltAt = -1L;
        List<VillagerEntityMCA> young = List.of();
        final Needing hungry = new Needing();
        final Needing thirsty = new Needing();
    }

    private static final class Needing {
        private long builtAt = -1L;
        private final List<VillagerEntityMCA> villagers = new ArrayList<>();

        List<VillagerEntityMCA> of(List<VillagerEntityMCA> young, Predicate<VillagerEntityMCA> need, long now) {
            if (builtAt != now) {
                builtAt = now;
                villagers.clear();
                for (VillagerEntityMCA villager : young) {
                    if (villager.isAlive() && isYoung(villager) && need.test(villager)) villagers.add(villager);
                }
            }
            return villagers;
        }
    }
}
