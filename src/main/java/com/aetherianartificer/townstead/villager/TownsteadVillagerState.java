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

    /**
     * Read the origin id straight out of the villager's own persisted snapshot (works client-side too).
     * Used to recover a carried/reconstructed entity's species when no live per-entity sync exists for it
     * (CarryOn rebuilds the entity from NBT with a fresh id that was never tracked). Empty if none.
     */
    public static String snapshotOriginId(VillagerEntityMCA villager) {
        return loadSnapshotTag(villager).getCompound("life").getString("originId");
    }

    /** The variant id this villager carries for {@code geneId} in its persisted snapshot, or empty. */
    public static String snapshotCarriedVariant(VillagerEntityMCA villager, String geneId) {
        if (geneId == null || geneId.isEmpty()) return "";
        return loadSnapshotTag(villager).getCompound("life").getCompound("carriedVariants").getString(geneId);
    }

    /** The expressed allele encodings persisted in the villager's snapshot (render fallback for carried entities). */
    public static java.util.List<String> snapshotExpressedAlleles(VillagerEntityMCA villager) {
        net.minecraft.nbt.ListTag list = loadSnapshotTag(villager).getCompound("life")
                .getList("expressedAlleles", net.minecraft.nbt.Tag.TAG_STRING);
        java.util.List<String> out = new java.util.ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) out.add(list.getString(i));
        return out;
    }

    static void loadInto(VillagerEntityMCA villager, TownsteadVillager state) {
        CompoundTag snapshot = loadSnapshotTag(villager);
        if (snapshot.contains("schema") && snapshot.getInt("schema") >= TownsteadVillager.SCHEMA_VERSION) {
            state.loadSnapshotTag(snapshot);
            return;
        }
        if (snapshot.contains("schema")) {
            state.loadSnapshotTag(snapshot);
            state.upgradeFromLegacyRoot(loadLegacyRoot(villager, false));
            saveSnapshot(villager, state);
            state.clearDirty();
            return;
        }
        CompoundTag legacy = loadLegacyRoot(villager, true);
        state.migrateLegacyRoot(legacy);
        saveSnapshot(villager, state);
        state.clearDirty();
    }

    static void saveSnapshot(VillagerEntityMCA villager, TownsteadVillager state) {
        saveSnapshotTag(villager, state.toSnapshotTag());
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

    private static CompoundTag loadLegacyRoot(VillagerEntityMCA villager, boolean preferNestedSnapshot) {
        CompoundTag root = new CompoundTag();
        //? if neoforge {
        root.put("hunger", villager.getData(Townstead.HUNGER_DATA).copy());
        root.put("thirst", villager.getData(Townstead.THIRST_DATA).copy());
        root.put("fatigue", villager.getData(Townstead.FATIGUE_DATA).copy());
        root.put("shift", villager.getData(Townstead.SHIFT_DATA).copy());
        CompoundTag life = villager.getData(Townstead.LIFE_DATA).copy();
        if (preferNestedSnapshot && life.contains(SNAPSHOT_KEY)) {
            CompoundTag nested = life.getCompound(SNAPSHOT_KEY);
            if (nested.contains("needs")) return expandSnapshot(nested);
        }
        life.remove(SNAPSHOT_KEY);
        root.put("life", life);
        //?} else if forge {
        /*CompoundTag persistent = villager.getPersistentData();
        if (preferNestedSnapshot && persistent.contains(SNAPSHOT_KEY)) {
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
