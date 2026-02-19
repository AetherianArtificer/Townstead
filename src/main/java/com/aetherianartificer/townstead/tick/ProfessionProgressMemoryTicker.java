package com.aetherianartificer.townstead.tick;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public final class ProfessionProgressMemoryTicker {
    private static final String KEY_LAST_PROFESSION = "townsteadLastProfession";
    private static final String KEY_PROFESSION_PROGRESS = "townsteadProfessionProgress";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_XP = "xp";

    private ProfessionProgressMemoryTicker() {}

    public static void tick(VillagerEntityMCA villager) {
        CompoundTag data = villager.getData(Townstead.HUNGER_DATA);
        String currentKey = professionKey(villager.getVillagerData().getProfession());
        if (currentKey == null) return;

        String lastKey = data.getString(KEY_LAST_PROFESSION);
        boolean changed = !lastKey.isBlank() && !lastKey.equals(currentKey);

        CompoundTag allProgress = data.getCompound(KEY_PROFESSION_PROGRESS);
        if (changed && isTrackable(currentKey) && allProgress.contains(currentKey, CompoundTag.TAG_COMPOUND)) {
            CompoundTag saved = allProgress.getCompound(currentKey);
            int savedLevel = Math.max(1, saved.getInt(KEY_LEVEL));
            int savedXp = Math.max(0, saved.getInt(KEY_XP));
            if (savedLevel != villager.getVillagerData().getLevel() || savedXp != villager.getVillagerXp()) {
                VillagerData vd = villager.getVillagerData();
                villager.setVillagerData(vd.setLevel(savedLevel));
                villager.setVillagerXp(savedXp);
            }
        }

        if (isTrackable(currentKey)) {
            CompoundTag mine = new CompoundTag();
            mine.putInt(KEY_LEVEL, Math.max(1, villager.getVillagerData().getLevel()));
            mine.putInt(KEY_XP, Math.max(0, villager.getVillagerXp()));
            allProgress.put(currentKey, mine);
            data.put(KEY_PROFESSION_PROGRESS, allProgress);
        }

        data.putString(KEY_LAST_PROFESSION, currentKey);
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

