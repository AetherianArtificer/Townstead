package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.api.v1.TownsteadApiV1;
import com.aetherianartificer.townstead.api.v1.event.WorkCompletedEvent;
import com.aetherianartificer.townstead.api.v1.model.VillageId;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
//? if >=1.21 {
import net.minecraft.core.HolderLookup;
//?}

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * How much of each crop every village has harvested, by the product brought in. Stories use it
 * to name what a village really lives on.
 */
public final class HarvestTally extends SavedData {
    public static final String FILE_ID = "townstead_harvests";
    private static final String HARVESTED = "townstead:harvested";
    private static boolean registered;

    private final Map<String, Map<String, Long>> byVillage = new LinkedHashMap<>();

    public static synchronized void init() {
        if (registered) return;
        registered = true;
        TownsteadApiV1.get().events().subscribe(WorkCompletedEvent.class, HarvestTally::onWork);
    }

    private static void onWork(WorkCompletedEvent event) {
        if (!HARVESTED.equals(event.verb()) || event.objectId().isEmpty()) return;
        if (!(event.worker().level() instanceof ServerLevel level)) return;
        TownRange.at(level, event.worker().blockPosition()).ifPresent(village ->
                get(level.getServer()).add(new VillageId(level.dimension().location(), village.getId()),
                        event.objectId().get(), Math.max(1, Math.round(event.magnitude()))));
    }

    public static HarvestTally get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(HarvestTally::new, HarvestTally::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(
                HarvestTally::load, HarvestTally::new, FILE_ID);
        *///?}
    }

    public void add(VillageId village, ResourceLocation product, long amount) {
        byVillage.computeIfAbsent(key(village), k -> new LinkedHashMap<>()).merge(product.toString(), amount, Long::sum);
        setDirty();
    }

    /** The product this village has harvested most, or null before any harvest. */
    public @Nullable ResourceLocation most(VillageId village) {
        Map<String, Long> counts = byVillage.get(key(village));
        if (counts == null || counts.isEmpty()) return null;
        String best = null;
        long bestCount = -1;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        return ResourceLocation.tryParse(best);
    }

    private static String key(VillageId village) {
        return village.dimension() + "|" + village.villageId();
    }

    //? if >=1.21 {
    public static HarvestTally load(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public static HarvestTally load(CompoundTag tag) {
    *///?}
        HarvestTally data = new HarvestTally();
        CompoundTag villages = tag.getCompound("villages");
        for (String village : villages.getAllKeys()) {
            CompoundTag products = villages.getCompound(village);
            Map<String, Long> counts = new LinkedHashMap<>();
            for (String product : products.getAllKeys()) counts.put(product, products.getLong(product));
            data.byVillage.put(village, counts);
        }
        return data;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag villages = new CompoundTag();
        byVillage.forEach((village, counts) -> {
            CompoundTag products = new CompoundTag();
            counts.forEach(products::putLong);
            villages.put(village, products);
        });
        tag.put("villages", villages);
        return tag;
    }
}
