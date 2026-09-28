package com.aetherianartificer.townstead.compat.vampirism;

import com.aetherianartificer.townstead.chronicle.emit.ChronicleTapKeys;
import com.aetherianartificer.townstead.chronicle.emit.ChronicleTaps;
import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Watches who holds each town's Vampirism totem and records the day it changes hands: a town
 * falling to the vampires, or one taken back from them. The first look only notes who holds it.
 */
public final class VampireTotemWatch extends SavedData {
    private static final String FILE_ID = "townstead_totem_control";
    private static final int INTERVAL = 1200;
    private static final String VAMPIRE = "vampire", OTHER = "other";

    private final Map<String, String> control = new HashMap<>();

    public static void tick(MinecraftServer server) {
        if (server.overworld().getGameTime() % INTERVAL != 0) return;
        Object vampires = VampireVillagers.vampireFaction();
        if (vampires == null) return;
        VampireTotemWatch watch = get(server);
        for (Faction faction : PoliticalSavedData.get(server).factions()) {
            if (!faction.active()) continue;
            for (SettlementRef settlement : faction.settlements()) {
                ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, settlement.dimension()));
                Village village = level == null ? null : VillageManager.get(level).getOrEmpty(settlement.villageId()).orElse(null);
                if (village == null) continue;
                BlockPos center = new BlockPos(village.getCenter());
                if (!level.isLoaded(center)) continue;
                Object controller = VampireTotemWelcome.controller(level, center);
                String now = controller == null ? OTHER : vampires.equals(controller) ? VAMPIRE : OTHER;
                String key = settlement.dimension() + "#" + settlement.villageId();
                String before = watch.control.get(key);
                if (now.equals(before)) continue;
                if (before != null) {
                    LivingEntity witness = resident(level, village);
                    if (witness == null) continue;
                    ChronicleTaps.survival(witness, VAMPIRE.equals(now) ? ChronicleTapKeys.VILLAGE_FELL_TO_VAMPIRES
                            : ChronicleTapKeys.VILLAGE_RETAKEN, Map.of("village", village.getName()));
                    FactionRelations.invalidate();
                }
                watch.control.put(key, now);
                watch.setDirty();
            }
        }
    }

    private static @Nullable LivingEntity resident(ServerLevel level, Village village) {
        for (UUID id : village.getResidentsUUIDs().toList()) {
            Entity entity = id == null ? null : level.getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive()) return living;
        }
        return null;
    }

    private static VampireTotemWatch get(MinecraftServer server) {
        //? if >=1.21 {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(VampireTotemWatch::new, VampireTotemWatch::load), FILE_ID);
        //?} else {
        /*return server.overworld().getDataStorage().computeIfAbsent(VampireTotemWatch::load, VampireTotemWatch::new, FILE_ID);
        *///?}
    }

    //? if >=1.21 {
    private static VampireTotemWatch load(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*private static VampireTotemWatch load(CompoundTag tag) {
    *///?}
        VampireTotemWatch watch = new VampireTotemWatch();
        CompoundTag values = tag.getCompound("control");
        for (String key : values.getAllKeys()) watch.control.put(key, values.getString(key));
        return watch;
    }

    @Override
    //? if >=1.21 {
    public CompoundTag save(CompoundTag tag, net.minecraft.core.HolderLookup.Provider provider) {
    //?} else {
    /*public CompoundTag save(CompoundTag tag) {
    *///?}
        CompoundTag values = new CompoundTag();
        control.forEach(values::putString);
        tag.put("control", values);
        return tag;
    }
}
