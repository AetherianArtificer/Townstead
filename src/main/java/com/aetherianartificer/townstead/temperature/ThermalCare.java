package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import com.aetherianartificer.townstead.shift.VillagerSchedules;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.schedule.Activity;

/** Short, expiring ownership of thermal travel so dressing, provisions and relief cannot fight. */
public final class ThermalCare {
    private static final String OWNER = "townstead_thermal_care", UNTIL = "townstead_thermal_care_until";
    private ThermalCare() {}
    public static String owner(VillagerEntityMCA villager) {
        var data = villager.getPersistentData();
        if (data.getLong(UNTIL) > villager.level().getGameTime()) return data.getString(OWNER);
        if (data.contains(OWNER)) release(villager, data.getString(OWNER));
        return "";
    }
    public static boolean available(VillagerEntityMCA villager, String action) {
        String owner = owner(villager);
        return owner.isEmpty() || owner.equals(action) || owner.equals("relief") && (action.equals("dress") || action.equals("supply"));
    }
    public static void hold(VillagerEntityMCA villager, String action) {
        villager.getPersistentData().putString(OWNER, action);
        villager.getPersistentData().putLong(UNTIL, villager.level().getGameTime() + 100);
        TownsteadVillagers.get(villager).needs().setSeekingRelief(true);
    }
    public static void release(VillagerEntityMCA villager, String action) {
        if (!action.equals(villager.getPersistentData().getString(OWNER))) return;
        villager.getPersistentData().remove(OWNER);
        villager.getPersistentData().remove(UNTIL);
        TownsteadVillagers.get(villager).needs().setSeekingRelief(false);
    }
    public static boolean needsBreak(VillagerEntityMCA villager, ThermalExposure exposure) {
        var needs = TownsteadVillagers.get(villager).needs();
        if (villager.isInWater() && exposure.load() <= -12) return true;
        var core = TemperatureData.tier(needs.bodyTempTenths(), exposure.profile());
        return BodyHeat.needsBreak(TemperatureData.celsius(needs.bodyTempTenths()), exposure.profile())
                && com.aetherianartificer.townstead.tick.TemperatureVillagerTicker.thermalBreakReady(villager, core);
    }

    /** Thermal errands yield to threats, urgent needs, and bedtime once the body is safe enough. */
    public static boolean interrupted(VillagerEntityMCA villager) {
        var needs = TownsteadVillagers.get(villager).needs();
        return villager.isSleeping() || villager.getVillagerBrain().isPanicking()
                || villager.getLastHurtByMob() != null
                || com.aetherianartificer.townstead.hunger.RefuelTask.emergency(needs)
                || BodyHeat.restTakesPriority(
                        needs.restOverrideActive() || VillagerSchedules.currentActivity(villager) == Activity.REST,
                        TemperatureData.Tier.values()[needs.coreThermalTier()],
                        villager.isInWater() && needs.comfortLoad() <= -12f);
    }
}
