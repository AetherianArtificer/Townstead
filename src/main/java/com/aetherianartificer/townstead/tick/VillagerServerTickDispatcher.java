package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.thirst.ThirstBridgeResolver;
import net.conczin.mca.entity.VillagerEntityMCA;

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
            LAST_TICK.remove(villager.getId());
            return;
        }

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
    }
}
