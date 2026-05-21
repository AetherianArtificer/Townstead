package com.aetherianartificer.townstead.villager;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.nbt.CompoundTag;

/**
 * Loader persistence adapter and temporary legacy facade.
 *
 * <p>The runtime source of truth is {@link TownsteadVillagers} and
 * {@link TownsteadVillager}. NBT is used here only to import old attachment
 * data and write compact save snapshots back through Minecraft's save format.</p>
 */
public final class TownsteadVillagerState {
    private static final String SNAPSHOT_KEY = "townstead:state";

    private TownsteadVillagerState() {}

    public static TownsteadVillager get(VillagerEntityMCA villager) {
        return TownsteadVillagers.get(villager);
    }

    public static TownsteadVillager root(VillagerEntityMCA villager) {
        return get(villager);
    }

    static void loadInto(VillagerEntityMCA villager, TownsteadVillager state) {
        CompoundTag snapshot = loadSnapshotTag(villager);
        if (snapshot.contains("schema") && snapshot.getInt("schema") >= TownsteadVillager.SCHEMA_VERSION) {
            state.loadSnapshotTag(snapshot);
            return;
        }
        CompoundTag legacy = loadLegacyRoot(villager);
        state.migrateLegacyRoot(legacy);
        saveSnapshot(villager, state);
        state.clearDirty();
    }

    static void saveSnapshot(VillagerEntityMCA villager, TownsteadVillager state) {
        saveSnapshotTag(villager, state.toSnapshotTag());
    }

    public static CompoundTag hunger(VillagerEntityMCA villager) {
        return get(villager).needs().hungerTag();
    }

    public static void saveHunger(VillagerEntityMCA villager, CompoundTag tag) {
        get(villager).needs().loadHunger(tag);
    }

    public static CompoundTag thirst(VillagerEntityMCA villager) {
        return get(villager).needs().thirstTag();
    }

    public static void saveThirst(VillagerEntityMCA villager, CompoundTag tag) {
        get(villager).needs().loadThirst(tag);
    }

    public static CompoundTag fatigue(VillagerEntityMCA villager) {
        return get(villager).needs().fatigueTag();
    }

    public static void saveFatigue(VillagerEntityMCA villager, CompoundTag tag) {
        get(villager).needs().loadFatigue(tag);
    }

    public static CompoundTag shift(VillagerEntityMCA villager) {
        return get(villager).schedule().toTag();
    }

    public static void saveShift(VillagerEntityMCA villager, CompoundTag tag) {
        get(villager).schedule().load(tag);
    }

    public static CompoundTag life(VillagerEntityMCA villager) {
        return get(villager).life().toTag();
    }

    public static void saveLife(VillagerEntityMCA villager, CompoundTag tag) {
        get(villager).life().load(tag);
    }

    private static CompoundTag loadSnapshotTag(VillagerEntityMCA villager) {
        //? if neoforge {
        CompoundTag carrier = villager.getData(Townstead.LIFE_DATA);
        return carrier.getCompound(SNAPSHOT_KEY);
        //?} else if forge {
        /*return villager.getPersistentData().getCompound(SNAPSHOT_KEY);
        *///?}
    }

    private static void saveSnapshotTag(VillagerEntityMCA villager, CompoundTag snapshot) {
        //? if neoforge {
        CompoundTag carrier = villager.getData(Townstead.LIFE_DATA);
        carrier.put(SNAPSHOT_KEY, snapshot);
        villager.setData(Townstead.LIFE_DATA, carrier);
        //?} else if forge {
        /*villager.getPersistentData().put(SNAPSHOT_KEY, snapshot);
        *///?}
    }

    private static CompoundTag loadLegacyRoot(VillagerEntityMCA villager) {
        CompoundTag root = new CompoundTag();
        //? if neoforge {
        root.put("hunger", villager.getData(Townstead.HUNGER_DATA).copy());
        root.put("thirst", villager.getData(Townstead.THIRST_DATA).copy());
        root.put("fatigue", villager.getData(Townstead.FATIGUE_DATA).copy());
        root.put("shift", villager.getData(Townstead.SHIFT_DATA).copy());
        CompoundTag life = villager.getData(Townstead.LIFE_DATA).copy();
        if (life.contains(SNAPSHOT_KEY)) {
            CompoundTag nested = life.getCompound(SNAPSHOT_KEY);
            if (nested.contains("needs")) return expandSnapshot(nested);
        }
        life.remove(SNAPSHOT_KEY);
        root.put("life", life);
        //?} else if forge {
        /*CompoundTag persistent = villager.getPersistentData();
        if (persistent.contains(SNAPSHOT_KEY)) {
            CompoundTag nested = persistent.getCompound(SNAPSHOT_KEY);
            if (nested.contains("needs")) return expandSnapshot(nested);
        }
        root.put("hunger", persistent.getCompound("townstead_hunger").copy());
        root.put("thirst", persistent.getCompound("townstead_thirst").copy());
        root.put("fatigue", persistent.getCompound("townstead_fatigue").copy());
        root.put("shift", persistent.getCompound("townstead_shift").copy());
        root.put("life", persistent.getCompound("townstead_life").copy());
        *///?}
        return root;
    }

    private static CompoundTag expandSnapshot(CompoundTag snapshot) {
        CompoundTag root = new CompoundTag();
        CompoundTag needs = snapshot.getCompound("needs");
        root.put("hunger", needs.getCompound("hunger"));
        root.put("thirst", needs.getCompound("thirst"));
        root.put("fatigue", needs.getCompound("fatigue"));
        root.put("shift", snapshot.getCompound("schedule"));
        root.put("life", snapshot.getCompound("life"));
        return root;
    }
}
