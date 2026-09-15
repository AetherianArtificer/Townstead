package com.aetherianartificer.townstead.shift;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/**
 * Pushes a villager's schedule to every client tracking it after a server-side write. The
 * editor paths in the mod entry points do the same inline; this is the loader-neutral seam the
 * public API writes through.
 */
public final class ShiftScheduleSync {
    private ShiftScheduleSync() {}

    public static void broadcastShifts(VillagerEntityMCA villager, int[] shifts) {
        ShiftSyncPayload sync = new ShiftSyncPayload(villager.getUUID(), shifts);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, sync);
        *///?}
    }

    public static void broadcastWeek(VillagerEntityMCA villager) {
        CompoundTag shiftTag = TownsteadVillagers.get(villager).schedule().toTag();
        ShiftWeekSyncPayload sync = new ShiftWeekSyncPayload(villager.getUUID(),
                ShiftData.getMode(shiftTag), ShiftData.getWeekDayTemplates(shiftTag));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntity(villager, sync);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(villager, sync);
        *///?}
    }
}
