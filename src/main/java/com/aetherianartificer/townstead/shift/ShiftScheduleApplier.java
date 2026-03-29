package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.schedule.Schedule;

/**
 * Utility to apply a custom TownsteadSchedule to a villager's brain
 * based on their stored shift data.
 */
public final class ShiftScheduleApplier {

    private ShiftScheduleApplier() {}

    /**
     * Read the villager's shift attachment and set a custom schedule on their brain.
     * If no custom shifts are stored, leave MCA's existing schedule untouched.
     */
    public static void apply(VillagerEntityMCA villager) {
        if (villager.getBrain() == null) return;

        //? if neoforge {
        CompoundTag shiftTag = villager.getData(Townstead.SHIFT_DATA);
        //?} else if forge {
        /*CompoundTag shiftTag = villager.getPersistentData().getCompound("townstead_shift");
        *///?}
        if (!ShiftData.hasCustomShifts(shiftTag)) {
            // No Townstead override — preserve the schedule MCA selected for this villager.
            return;
        }

        int[] shifts = ShiftData.getShifts(shiftTag);
        villager.getBrain().setSchedule(new TownsteadSchedule(shifts));
    }

    /**
     * Temporarily override the villager's schedule to all-REST.
     * Used when the villager needs fatigue recovery outside their normal rest hours.
     * Call {@link #apply(VillagerEntityMCA)} to restore the original schedule.
     */
    public static void overrideToRest(VillagerEntityMCA villager) {
        if (villager.getBrain() == null) return;
        int[] allRest = new int[ShiftData.HOURS_PER_DAY];
        // REST ordinal from ShiftData.ORDINAL_TO_ACTIVITY
        int restOrdinal = 3; // REST
        java.util.Arrays.fill(allRest, restOrdinal);
        villager.getBrain().setSchedule(new TownsteadSchedule(allRest));
    }
}
