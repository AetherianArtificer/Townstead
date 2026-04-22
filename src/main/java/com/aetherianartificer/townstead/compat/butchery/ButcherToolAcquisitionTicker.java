package com.aetherianartificer.townstead.compat.butchery;

import com.aetherianartificer.townstead.hunger.ButcherSupplyManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps butcher villagers supplied with their trade tools by pulling
 * cleavers and skinning knives out of nearby chests and barrels when they
 * don't already carry one. Without this, the player has to hand-place
 * tools directly into the villager's inventory before the butcher will
 * start work, which the rest of the supply chain (raw inputs, fuel,
 * output) doesn't require.
 *
 * <p>Throttled per villager: an actual container scan fires at most once
 * per {@value #PULL_INTERVAL_TICKS} ticks, and only while the butcher is
 * missing at least one tool. A butcher already carrying both blades costs
 * nothing here.
 */
public final class ButcherToolAcquisitionTicker {
    private static final int PULL_INTERVAL_TICKS = 60;

    private static final Map<UUID, Long> NEXT_PULL_TICK = new ConcurrentHashMap<>();

    private ButcherToolAcquisitionTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!ButcheryCompat.isLoaded()) return;
        if (villager.getVillagerData().getProfession() != VillagerProfession.BUTCHER) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (!onWorkShift(villager, level)) return;

        boolean needsCleaver = !ButcherToolDamage.hasCleaver(villager);
        boolean needsKnife = !ButcherToolDamage.hasKnife(villager);
        boolean needsHacksaw = !ButcherToolDamage.hasHacksaw(villager);
        if (!needsCleaver && !needsKnife && !needsHacksaw) {
            NEXT_PULL_TICK.remove(villager.getUUID());
            return;
        }

        long gameTime = level.getGameTime();
        Long next = NEXT_PULL_TICK.get(villager.getUUID());
        if (next != null && gameTime < next) return;
        NEXT_PULL_TICK.put(villager.getUUID(), gameTime + PULL_INTERVAL_TICKS);

        if (needsCleaver) {
            ButcherSupplyManager.pullCleaver(level, villager, villager.blockPosition());
        }
        if (needsKnife) {
            ButcherSupplyManager.pullKnife(level, villager, villager.blockPosition());
        }
        if (needsHacksaw) {
            ButcherSupplyManager.pullHacksaw(level, villager, villager.blockPosition());
        }
    }

    public static void forget(VillagerEntityMCA villager) {
        NEXT_PULL_TICK.remove(villager.getUUID());
    }

    private static boolean onWorkShift(VillagerEntityMCA villager, ServerLevel level) {
        Brain<?> brain = villager.getBrain();
        long dayTime = level.getDayTime() % 24000L;
        return brain.getSchedule().getActivityAt((int) dayTime) == Activity.WORK;
    }
}
