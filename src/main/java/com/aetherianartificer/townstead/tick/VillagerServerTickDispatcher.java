package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.butchery.ButcherToolAcquisitionTicker;
import com.aetherianartificer.townstead.compat.butchery.ButcheryComplaintsTicker;
import com.aetherianartificer.townstead.compat.butchery.SkinRackJob;
import com.aetherianartificer.townstead.diagnostics.TownsteadProfiler;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerComplaintsTicker;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerSupplyAcquisitionTicker;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.EmergencyBedClaims;
import com.aetherianartificer.townstead.storage.EmptyContainerDropoff;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class VillagerServerTickDispatcher {
    private VillagerServerTickDispatcher() {}

    // Guard against double-ticking caused by Sinytra Connector dispatching
    // MCA's tick event through both Forge and Fabric paths.
    private static final Map<Integer, Long> LAST_TICK = new ConcurrentHashMap<>();

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;

        long gameTime = villager.level().getGameTime();
        Long lastTick = LAST_TICK.get(villager.getId());
        if (lastTick != null && lastTick == gameTime) return;
        LAST_TICK.put(villager.getId(), gameTime);

        // Clean up dead/removed entities
        if (!villager.isAlive() || villager.isRemoved()) {
            if (villager.level() instanceof ServerLevel level) {
                EmergencyBedClaims.releaseAll(level, villager.getUUID());
            }
            BedOccupancySanitizer.forget(villager);
            WorkToolTicker.forget(villager);
            ButcherToolAcquisitionTicker.forget(villager);
            LeatherworkerSupplyAcquisitionTicker.forget(villager);
            SkinRackJob.forget(villager);
            EmptyContainerDropoff.forget(villager);
            LAST_TICK.remove(villager.getId());
            return;
        }

        profile("villager.bed_occupancy", () -> BedOccupancySanitizer.tick(villager));
        profile("villager.cook_auto_assign", () -> CookAutoAssignTicker.tick(villager));
        profile("villager.barista_auto_assign", () -> BaristaAutoAssignTicker.tick(villager));
        profile("villager.cook_trade_backfill", () -> CookTradeBackfillTicker.tick(villager));
        profile("villager.barista_trade_backfill", () -> BaristaTradeBackfillTicker.tick(villager));
        profile("villager.hunger", () -> HungerVillagerTicker.tick(villager));
        if (ThirstBridgeResolver.isActive()) {
            profile("villager.thirst", () -> ThirstVillagerTicker.tick(villager));
        }
        profile("villager.fatigue", () -> FatigueVillagerTicker.tick(villager));
        profile("villager.container_dropoff", () -> EmptyContainerDropoff.tick(villager));
        profile("villager.profession_memory", () -> ProfessionProgressMemoryTicker.tick(villager));
        profile("villager.guard_rest", () -> GuardRestEnforcerTicker.tick(villager));
        profile("villager.butcher_tool_acquire", () -> ButcherToolAcquisitionTicker.tick(villager));
        profile("villager.leatherworker_supply", () -> LeatherworkerSupplyAcquisitionTicker.tick(villager));
        profile("villager.work_tool", () -> WorkToolTicker.tick(villager));
        profile("villager.butchery_complaints", () -> ButcheryComplaintsTicker.tick(villager));
        profile("villager.leatherworker_complaints", () -> LeatherworkerComplaintsTicker.tick(villager));
        profile("villager.reaction_lock", () ->
                com.aetherianartificer.townstead.reaction.ReactionLockTracker.tickFreeze(villager, gameTime));
        profile("villager.reaction_context", () ->
                com.aetherianartificer.townstead.reaction.trigger.event.ContextTickHook.tick(villager, gameTime));
        profile("villager.life_stamper", () ->
                com.aetherianartificer.townstead.calendar.VillagerLifeStamper.tick(villager));
        profile("villager.life_stage", () -> LifeStageTicker.tick(villager));
    }

    private static void profile(String name, Runnable runnable) {
        if (!TownsteadProfiler.enabled()) {
            runnable.run();
            return;
        }
        long start = System.nanoTime();
        try {
            runnable.run();
        } finally {
            TownsteadProfiler.record(name, System.nanoTime() - start);
        }
    }
}
