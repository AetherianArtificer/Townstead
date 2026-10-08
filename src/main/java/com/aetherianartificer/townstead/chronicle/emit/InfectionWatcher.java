package com.aetherianartificer.townstead.chronicle.emit;

import net.conczin.mca.entity.VillagerEntityMCA;

import java.util.Map;

/**
 * The game moments of a zombie infection: the bite that starts it, a cure before it runs its course, and the
 * turn when it does. MCA moves every infection through {@code setInfectionProgress}, so the change in progress
 * is the whole story.
 */
public final class InfectionWatcher {
    private InfectionWatcher() {}

    public static void onProgress(VillagerEntityMCA villager, float before, float after) {
        if (villager.level().isClientSide || before == after) return;
        if (before <= 0f && after > 0f) ChronicleTaps.survival(villager, ChronicleTapKeys.ZOMBIE_BITE, Map.of());
        else if (before > 0f && after <= 0f && villager.isAlive()) ChronicleTaps.survival(villager, ChronicleTapKeys.INFECTION_CURED, Map.of());
        else if (before <= 1f && after > 1f) ChronicleTaps.survival(villager, ChronicleTapKeys.TURNED_ZOMBIE, Map.of());
    }
}
