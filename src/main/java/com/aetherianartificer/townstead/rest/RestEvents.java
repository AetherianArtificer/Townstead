package com.aetherianartificer.townstead.rest;

import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.root.gene.types.RestGeneType;
import com.aetherianartificer.townstead.root.trigger.GeneTriggers;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Watches each villager and player fall asleep and wake, for every kind of rest spot alike: runs
 * the spot's {@code on_sleep} and {@code on_wake}, fires the {@code when_sleep}, {@code when_wake}
 * and {@code when_rested} triggers, and wakes anyone a {@code pheno:rest} gene keeps up.
 */
public final class RestEvents {
    private static final int PREVENT_CHECK = 20;

    private static final class Sleep {
        final BlockPos pos;
        boolean rested;
        Sleep(BlockPos pos) { this.pos = pos; }
    }

    private static final Map<LivingEntity, Sleep> SLEEPING = new WeakHashMap<>();

    private RestEvents() {}

    public static void tick(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        boolean asleep = entity.isSleeping();
        Sleep sleep;
        synchronized (SLEEPING) {
            sleep = SLEEPING.get(entity);
        }
        if (asleep && sleep == null) {
            if (prevented(entity)) return;
            BlockPos pos = entity.getSleepingPos().orElse(entity.blockPosition());
            synchronized (SLEEPING) {
                SLEEPING.put(entity, new Sleep(pos));
            }
            RestSpot spot = RestSpots.used(entity, entity.level().getBlockState(pos));
            if (spot != null && spot.onSleep() != null) spot.onSleep().run(new ActionContext(entity));
            GeneTriggers.onSleep(entity);
        } else if (!asleep && sleep != null) {
            synchronized (SLEEPING) {
                SLEEPING.remove(entity);
            }
            RestSpot spot = RestSpots.used(entity, entity.level().getBlockState(sleep.pos));
            if (spot != null && spot.onWake() != null) spot.onWake().run(new ActionContext(entity));
            GeneTriggers.onWakeUp(entity);
        } else if (asleep) {
            if (entity.tickCount % PREVENT_CHECK == 0 && prevented(entity)) return;
            if (!sleep.rested && rested(entity)) {
                sleep.rested = true;
                GeneTriggers.onRested(entity);
            }
        }
    }

    /** The spot this entity is sleeping in, or null when it is awake or in none. */
    public static @Nullable RestSpot sleepingIn(LivingEntity entity) {
        if (!entity.isSleeping()) return null;
        BlockPos pos = entity.getSleepingPos().orElse(null);
        return pos == null ? null : RestSpots.used(entity, entity.level().getBlockState(pos));
    }

    /** A gene keeps this sleeper up: it wakes, and a player is told why. */
    private static boolean prevented(LivingEntity entity) {
        RestGeneType.Instance gene = RestGenes.preventing(entity);
        if (gene == null) return false;
        wake(entity);
        if (entity instanceof ServerPlayer player && !gene.message().isEmpty()) {
            player.displayClientMessage(Component.translatable(gene.message()), true);
        }
        return true;
    }

    /** Wakes the entity: a player gets out of bed, anyone else simply stops sleeping. */
    public static void wake(LivingEntity entity) {
        if (!entity.isSleeping()) return;
        if (entity instanceof Player player) player.stopSleepInBed(true, true);
        else entity.stopSleeping();
    }

    private static boolean rested(LivingEntity entity) {
        if (entity instanceof Player player) return player.isSleepingLongEnough();
        if (entity instanceof VillagerEntityMCA villager) return TownsteadVillagers.get(villager).needs().fatigue() <= 0;
        return false;
    }
}
