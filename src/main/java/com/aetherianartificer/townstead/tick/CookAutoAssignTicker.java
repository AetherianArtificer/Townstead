package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightCookAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class CookAutoAssignTicker {
    private static final int COOK_ASSIGN_INTERVAL_TICKS = 200;

    private CookAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (villager.tickCount % COOK_ASSIGN_INTERVAL_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;

        VillagerProfession current = villager.getVillagerData().getProfession();
        if (FarmersDelightCookAssignment.isExternalCookProfession(current)) {
            if (FarmersDelightCookAssignment.shouldLoseCookProfession(level, villager)) {
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
