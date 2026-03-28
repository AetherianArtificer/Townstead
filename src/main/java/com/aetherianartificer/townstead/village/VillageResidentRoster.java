package com.aetherianartificer.townstead.village;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.shift.ShiftData;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class VillageResidentRoster {
    private VillageResidentRoster() {}

    public static List<VillageResidentClientStore.Resident> snapshot(ServerPlayer player) {
        Optional<Village> villageOpt = Village.findNearest(player);
        if (villageOpt.isEmpty() || !villageOpt.get().isWithinBorder(player)) return List.of();

        Set<UUID> residentUuids = new LinkedHashSet<>();
        villageOpt.get().getResidentsUUIDs().forEach(residentUuids::add);
        if (residentUuids.isEmpty()) return List.of();

        List<VillageResidentClientStore.Resident> residents = new ArrayList<>(residentUuids.size());
        for (UUID residentUuid : residentUuids) {
            VillagerEntityMCA villager = findResident(player, residentUuid);
            if (villager == null) continue;
            if (AgeState.byCurrentAge(villager.getAge()) != AgeState.ADULT) continue;
            residents.add(new VillageResidentClientStore.Resident(
                    villager.getUUID(),
                    villager.getDisplayName().getString(),
                    professionKey(villager.getVillagerData().getProfession()),
                    villager.getVillagerData().getLevel(),
                    ShiftData.getShifts(villager.getData(Townstead.SHIFT_DATA))
            ));
        }
        residents.sort(Comparator.comparing(VillageResidentClientStore.Resident::name, String.CASE_INSENSITIVE_ORDER));
        return residents;
    }

    private static VillagerEntityMCA findResident(ServerPlayer player, UUID residentUuid) {
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(residentUuid);
            if (entity instanceof VillagerEntityMCA villager) return villager;
        }
        return null;
    }

    private static String professionKey(VillagerProfession profession) {
        ResourceLocation key = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return key != null ? key.toString() : "minecraft:none";
    }
}
