package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Non-butcher villagers occasionally comment on butchery-related buildings
 * in their home village. Flavor chatter only, never mechanical.
 *
 * <p>Keeps the village feeling alive: the miller mentions the smokehouse
 * smell, the sensitive villager dreads the slaughterhouse, the confident
 * trader talks up the tannery's leather. MCA's personality system picks
 * the right variant when a {@code <personality>.dialogue.chat.village_life.*}
 * key exists; otherwise the base line is used.
 */
public final class VillageLifeChatterTicker {
    /** ~10 in-game minutes between any given villager's chatter. */
    private static final long CHATTER_INTERVAL_TICKS = 12000L;
    /** Per-tick chance to actually emit once throttle allows. */
    private static final float EMIT_CHANCE = 0.003f;
    private static final double PLAYER_RANGE = 24.0;
    private static final String LAST_CHATTER_KEY = "townstead_lastVillageLifeChatter";

    private record BuildingChatter(String typeId, String langSuffix) {}

    /** Which building types get chatter, and the base lang suffix used. */
    private static final BuildingChatter[] CANDIDATES = new BuildingChatter[] {
            new BuildingChatter("compat/butchery/butcher_shop_l1", "butcher_shop"),
            new BuildingChatter("compat/butchery/butcher_shop_l2", "butcher_shop"),
            new BuildingChatter("compat/butchery/butcher_shop_l3", "butcher_shop"),
            new BuildingChatter("butcher", "butcher_shop"),
            new BuildingChatter("compat/butchery/slaughterhouse", "slaughterhouse"),
            new BuildingChatter("compat/butchery/smokehouse", "smokehouse"),
            new BuildingChatter("compat/butchery/tannery", "tannery")
    };

    private VillageLifeChatterTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return;
        // Butchers have their own richer chatter via ButcheryComplaintsTicker.
        if (villager.getVillagerData().getProfession() == VillagerProfession.BUTCHER) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!canChat(villager, level)) return;
        if (level.getNearestPlayer(villager, PLAYER_RANGE) == null) return;

        long gameTime = level.getGameTime();
        if (onThrottle(villager, gameTime)) return;
        if (level.random.nextFloat() >= EMIT_CHANCE) return;

        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return;

        List<String> availableSuffixes = collectAvailableSuffixes(villageOpt.get());
        if (availableSuffixes.isEmpty()) return;

        String suffix = availableSuffixes.get(level.random.nextInt(availableSuffixes.size()));
        int variant = 1 + level.random.nextInt(3);
        String key = "dialogue.chat.village_life." + suffix + "/" + variant;
        villager.sendChatToAllAround(key);
        markChattered(villager, gameTime);
    }

    private static List<String> collectAvailableSuffixes(Village village) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Building b : village.getBuildings().values()) {
            if (!b.isComplete()) continue;
            String type = b.getType();
            if (type == null) continue;
            for (BuildingChatter candidate : CANDIDATES) {
                if (type.equals(candidate.typeId()) && seen.add(candidate.langSuffix())) {
                    out.add(candidate.langSuffix());
                }
            }
        }
        return out;
    }

    private static boolean canChat(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        Activity current = brain.getSchedule().getActivityAt((int) dayTime);
        // Keep chatter to waking activities; nobody talks about the butcher shop while asleep.
        return current != Activity.REST;
    }

    private static boolean onThrottle(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        *///?}
        long last = data.getLong(LAST_CHATTER_KEY);
        return gameTime - last < CHATTER_INTERVAL_TICKS;
    }

    private static void markChattered(VillagerEntityMCA villager, long gameTime) {
        //? if neoforge {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        data.putLong(LAST_CHATTER_KEY, gameTime);
        villager.setData(Townstead.HUNGER_DATA, data);
        //?} else {
        /*CompoundTag data = villager.getPersistentData().getCompound("townstead_hunger");
        data.putLong(LAST_CHATTER_KEY, gameTime);
        villager.getPersistentData().put("townstead_hunger", data);
        *///?}
    }

    private static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }
}
