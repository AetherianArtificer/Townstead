package com.aetherianartificer.townstead.chronicle.emit;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * A zombie villager cured. The conversion is the game moment — someone spent
 * a golden apple and waited out the shaking — and the story belongs to the one
 * who came back, not to the thing they were.
 */
public final class CureWatcher {

    private CureWatcher() {}

    public static void onConverted(@Nullable LivingEntity before, @Nullable LivingEntity after) {
        if (!(before instanceof ZombieVillager)) return;
        if (!(after instanceof Villager || after instanceof net.conczin.mca.entity.VillagerEntityMCA)) {
            return;
        }
        ChronicleTaps.survival(after, ChronicleTapKeys.CURED, Map.of());
    }
}
