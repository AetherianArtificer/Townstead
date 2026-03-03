package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import com.aetherianartificer.townstead.compat.farmersdelight.FarmersDelightBaristaAssignment;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class BaristaAutoAssignTicker {
    private static final int BARISTA_ASSIGN_INTERVAL_TICKS = 200;

    private BaristaAutoAssignTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        if (!TownsteadConfig.isTownsteadCookEnabled()) return;
        if (!ModCompat.isLoaded("rusticdelight")) return;
        if (villager.tickCount % BARISTA_ASSIGN_INTERVAL_TICKS != 0) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (villager.isBaby() || !villager.isAlive() || villager.isSleeping()) return;

        VillagerProfession current = villager.getVillagerData().getProfession();
        if (FarmersDelightBaristaAssignment.isBaristaProfession(current)) {
            if (!FarmersDelightBaristaAssignment.canVillagerWorkAsBarista(level, villager)) {
                villager.setProfession(VillagerProfession.NONE);
            }
            return;
        }
        if (current != VillagerProfession.NONE) return;

        VillagerProfession baristaProfession = Townstead.BARISTA_PROFESSION.get();
        if (baristaProfession == null || baristaProfession == VillagerProfession.NONE) return;
        if (!FarmersDelightBaristaAssignment.hasAvailableBaristaSlot(level, villager)) return;

        villager.setProfession(baristaProfession);
    }
}
