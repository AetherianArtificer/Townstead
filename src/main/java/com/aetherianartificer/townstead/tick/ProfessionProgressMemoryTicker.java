package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.villager.TownsteadVillager;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ProfessionProgressMemoryTicker {
    private ProfessionProgressMemoryTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        TownsteadVillager.ProfessionMemory memory = TownsteadVillagers.get(villager).professionMemory();
        String currentKey = professionKey(villager.getVillagerData().getProfession());
        if (currentKey == null) return;

        String lastKey = memory.lastProfession();
        boolean changed = !lastKey.isBlank() && !lastKey.equals(currentKey);

        TownsteadVillager.ProfessionMemory.Progress saved = memory.progress(currentKey);
        if (changed && isTrackable(currentKey) && saved != null) {
            int savedLevel = saved.level();
            int savedXp = saved.xp();
            if (savedLevel != villager.getVillagerData().getLevel() || savedXp != villager.getVillagerXp()) {
                VillagerData vd = villager.getVillagerData();
                villager.setVillagerData(vd.setLevel(savedLevel));
                villager.setVillagerXp(savedXp);
            }
        }

        if (isTrackable(currentKey)) {
            memory.putProgress(currentKey, villager.getVillagerData().getLevel(), villager.getVillagerXp());
        }

        memory.setLastProfession(currentKey);
    }

    private static boolean isTrackable(String key) {
        return key != null && !key.isBlank() && !"minecraft:none".equals(key);
    }

    private static String professionKey(VillagerProfession profession) {
        if (profession == null) return null;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return id == null ? null : id.toString();
    }
}

