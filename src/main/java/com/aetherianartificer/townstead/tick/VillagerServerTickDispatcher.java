package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.butchery.ButcherToolAcquisitionTicker;
import com.aetherianartificer.townstead.compat.butchery.ButcheryComplaintsTicker;
import com.aetherianartificer.townstead.compat.butchery.SkinRackJob;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerComplaintsTicker;
import com.aetherianartificer.townstead.leatherworking.LeatherworkerSupplyAcquisitionTicker;
import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import com.aetherianartificer.townstead.fatigue.EmergencyBedClaims;
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
            LAST_TICK.remove(villager.getId());
            return;
        }

        BedOccupancySanitizer.tick(villager);
        CookAutoAssignTicker.tick(villager);
        BaristaAutoAssignTicker.tick(villager);
        CookTradeBackfillTicker.tick(villager);
        BaristaTradeBackfillTicker.tick(villager);
        HungerVillagerTicker.tick(villager);
        if (ThirstBridgeResolver.isActive()) {
            ThirstVillagerTicker.tick(villager);
        }
        FatigueVillagerTicker.tick(villager);
        ProfessionProgressMemoryTicker.tick(villager);
        GuardRestEnforcerTicker.tick(villager);
        ButcherToolAcquisitionTicker.tick(villager);
        LeatherworkerSupplyAcquisitionTicker.tick(villager);
        WorkToolTicker.tick(villager);
        ButcheryComplaintsTicker.tick(villager);
        LeatherworkerComplaintsTicker.tick(villager);
    }
}
