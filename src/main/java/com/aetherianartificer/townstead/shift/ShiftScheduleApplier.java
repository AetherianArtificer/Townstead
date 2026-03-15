package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/**
 * Utility to apply a custom TownsteadSchedule to a villager's brain
 * based on their stored shift data.
 */
public final class ShiftScheduleApplier {

    private ShiftScheduleApplier() {}

    /**
     * Read the villager's shift attachment and set a custom schedule on their brain.
     * If no custom shifts are stored, does nothing (the vanilla schedule remains).
     */
    public static void apply(VillagerEntityMCA villager) {
        //? if neoforge {
        CompoundTag shiftTag = villager.getData(Townstead.SHIFT_DATA);
        //?} else if forge {
        /*CompoundTag shiftTag = villager.getPersistentData().getCompound("townstead_shift");
        *///?}
        if (!ShiftData.hasCustomShifts(shiftTag)) return;

        int[] shifts = ShiftData.getShifts(shiftTag);
        villager.getBrain().setSchedule(new TownsteadSchedule(shifts));
    }
}
