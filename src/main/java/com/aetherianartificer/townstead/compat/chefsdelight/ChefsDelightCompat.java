package com.aetherianartificer.townstead.compat.chefsdelight;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ChefsDelightCompat {
    private ChefsDelightCompat() {}

    /**
     * Returns true if the given profession belongs to Chef's Delight
     * and should be blocked because Townstead cook mode is active.
     */
    public static boolean shouldBlockProfession(VillagerProfession profession) {
        if (!TownsteadConfig.isTownsteadCookEnabled()) return false;
        if (!ModCompat.isLoaded("chefsdelight")) return false;
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null && "chefsdelight".equals(key.getNamespace());
    }
}
