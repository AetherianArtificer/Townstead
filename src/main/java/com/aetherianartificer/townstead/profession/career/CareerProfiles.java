package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves the durable {@link CareerProfile} for any supported character. Read-side only:
 * villager mutations must go through the profession memory (for the dirty flag) and player
 * mutations through {@link PlayerCareers#mutate}.
 */
public final class CareerProfiles {
    private CareerProfiles() {}

    @Nullable
    public static CareerProfile of(LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            return TownsteadVillagers.get(villager).professionMemory().careerProfile();
        }
        if (entity instanceof Player player) return PlayerCareers.get(player);
        return null;
    }
}
