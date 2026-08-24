package com.aetherianartificer.townstead.profession;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Optional;
import java.util.Set;

public final class BaristaAssignment {
    private BaristaAssignment() {}

    /**
     * Whether this profession's def declares brew work. The runtime authority for who is a
     * barista; requires loaded profession defs.
     */
    public static boolean declaresBaristaWork(VillagerProfession profession) {
        return com.aetherianartificer.townstead.work.WorkTaskDeclarations.professionDeclares(
                profession, com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW);
    }

    /**
     * Startup/client-safe id check for contexts where profession defs are unavailable (the
     * trades event fires before datapacks load; dedicated-server clients have no defs).
     */
    public static boolean isBaristaProfession(VillagerProfession profession) {
        if (profession == null) return false;
        ResourceLocation id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession);
        return ScannedProfessions.idsForTask(
                com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW).contains(id);
    }

    public static boolean canVillagerWorkAsBarista(ServerLevel level, VillagerEntityMCA villager) {
        if (villager == null) return false;
        return declaresBaristaWork(villager.getVillagerData().getProfession());
    }

    public static boolean hasWorkingBarista(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = com.aetherianartificer.townstead.profession.ProfessionCapacity.resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!declaresBaristaWork(resident.getVillagerData().getProfession())) continue;
            if (assignedCafe(level, resident).isPresent()) return true;
        }
        return false;
    }

    public static boolean hasAvailableBaristaSlot(ServerLevel level, VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.profession.ProfessionSites
                .hasFreeSite(level, villager, baristaDef());
    }




    /** The café this villager works, from the shared walk. Posts are not café work: a barista
     *  brews at a counter, so only a building seat counts here. */
    public static Optional<Building> assignedCafe(ServerLevel level, VillagerEntityMCA villager) {
        return com.aetherianartificer.townstead.profession.ProfessionSites
                .assignedSite(level, villager, baristaDef())
                .map(com.aetherianartificer.townstead.profession.ProfessionSites.Site::building)
                .filter(java.util.Objects::nonNull);
    }

    public static Set<Long> assignedCafeBounds(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> cafe = assignedCafe(level, villager);
        if (cafe.isEmpty()) return Set.of();
        // The walkable room discovered from the world; see WorkSiteBounds.
        return com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, cafe.get());
    }

    /** The def whose work tasks declare brewing: the career the café machinery serves. */
    private static com.aetherianartificer.townstead.profession.def.ProfessionDef baristaDef() {
        return com.aetherianartificer.townstead.profession.ProfessionSites.defForTask(
                com.aetherianartificer.townstead.profession.def.WorkTaskTypes.BREW);
    }
}
