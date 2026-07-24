package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class CookAutoAssignTicker {
    private static final int COOK_ASSIGN_INTERVAL_TICKS = 200;

    private CookAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isTownsteadCookEnabled()) return;
        if (villager.tickCount % COOK_ASSIGN_INTERVAL_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;

        VillagerProfession current = villager.getVillagerData().getProfession();
        if (FarmersDelightCookAssignment.declaresCookWork(current)) {
            // Only demote what Townstead itself assigns. A POI-anchored cook profession
            // (Chef's Delight etc.) is hired and fired by its own mod's rules; stripping it
            // here would fight the brain's job-site claim in an endless flap.
            if (current == FarmersDelightCookAssignment.resolveAssignableCookProfession()
                    && FarmersDelightCookAssignment.shouldLoseCookProfession(level, villager)) {
                villager.setProfession(VillagerProfession.NONE);
            }
            return;
        }
        if (current != VillagerProfession.NONE) return;

        VillagerProfession cookProfession = FarmersDelightCookAssignment.resolveAssignableCookProfession();
        if (cookProfession == null) return;
        if (!FarmersDelightCookAssignment.hasAvailableCookSlot(level, villager)) return;

        villager.setProfession(cookProfession);
    }
}
