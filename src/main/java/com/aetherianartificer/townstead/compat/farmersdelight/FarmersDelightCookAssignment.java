package com.aetherianartificer.townstead.compat.farmersdelight;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class FarmersDelightCookAssignment {
    private static final String[] COOK_PROFESSION_IDS = new String[] {
            "townstead:cook",
            "chefsdelight:cook",
            "chefsdelight:chef",
            "vca:cook",
            "villagerclothingaddition:cook"
    };

    private FarmersDelightCookAssignment() {}

    /**
     * Whether this profession's def (directly or via aliases) declares cook-family work. The
     * runtime authority for who is a cook; requires loaded profession defs.
     */
    public static boolean declaresCookWork(VillagerProfession profession) {
        return com.aetherianartificer.townstead.work.WorkTaskDeclarations.professionDeclares(
                profession,
                com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK,
                com.aetherianartificer.townstead.profession.def.WorkTaskTypes.CHOP);
    }

    /**
     * Startup/client-safe id check for contexts where profession defs are unavailable: the
     * trades event fires before datapacks load, and dedicated-server clients have no defs.
     * Everything at runtime goes through {@link #declaresCookWork}.
     */
    public static boolean isExternalCookProfession(VillagerProfession profession) {
        if (profession == null) return false;
        for (String id : COOK_PROFESSION_IDS) {
            //? if >=1.21 {
            ResourceLocation key = ResourceLocation.parse(id);
            //?} else {
            /*ResourceLocation key = new ResourceLocation(id);
            *///?}
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) continue;
            if (BuiltInRegistries.VILLAGER_PROFESSION.get(key) == profession) return true;
        }
        return false;
    }

    /**
     * The registered profession to assign auto-promoted cooks. Careers are flat, but gated
     * careers (those declaring acquisition routes) can also declare cook work —
     * auto-promotion must target a career anyone can simply practice. Deterministic: practiced
     * beats gated, then lowest id, regardless of registry map order.
     */
    public static VillagerProfession resolveAssignableCookProfession() {
        com.aetherianartificer.townstead.profession.def.ProfessionDef best = null;
        for (com.aetherianartificer.townstead.profession.def.ProfessionDef def
                : com.aetherianartificer.townstead.profession.def.ProfessionDefs.all().values()) {
            boolean declares = false;
            for (com.aetherianartificer.townstead.profession.def.WorkTaskDef task : def.workTasks()) {
                if (task.type().equals(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK)) {
                    declares = true;
                    break;
                }
            }
            if (!declares) continue;
            if (!BuiltInRegistries.VILLAGER_PROFESSION.containsKey(def.id())) continue;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(def.id());
            if (profession == null || profession == VillagerProfession.NONE) continue;
            if (best == null || prefer(def, best)) best = def;
        }
        if (best == null) return null;
        return BuiltInRegistries.VILLAGER_PROFESSION.get(best.id());
    }

    private static boolean prefer(com.aetherianartificer.townstead.profession.def.ProfessionDef candidate,
                                  com.aetherianartificer.townstead.profession.def.ProfessionDef current) {
        boolean candidatePracticed = candidate.isRoot();
        boolean currentPracticed = current.isRoot();
        if (candidatePracticed != currentPracticed) return candidatePracticed;
        return candidate.id().compareTo(current.id()) < 0;
    }

    public static boolean canVillagerWorkAsCook(ServerLevel level, VillagerEntityMCA villager) {
        return assignedCookSite(level, villager).isPresent();
    }

    public static boolean hasAvailableCookSlot(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return false;
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return false;

        List<CookSite> sites = buildCookSites(level, village);
        if (sites.isEmpty()) return false;

        int activeCooks = 0;
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (declaresCookWork(resident.getVillagerData().getProfession())) {
                activeCooks++;
            }
        }
        return activeCooks < sites.size();
    }

    public static boolean shouldLoseCookProfession(ServerLevel level, VillagerEntityMCA villager) {
        return assignedCookSite(level, villager).isEmpty();
    }

    public static Optional<Village> resolveVillage(VillagerEntityMCA villager) {
        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().isWithinBorder(villager)) return home;
        Optional<Village> nearest = Village.findNearest(villager);
        if (nearest.isPresent() && nearest.get().isWithinBorder(villager)) return nearest;
        return Optional.empty();
    }

    private static boolean isEligibleVillageMember(Village village, VillagerEntityMCA villager) {
        if (!village.isWithinBorder(villager)) return false;

        Optional<Village> home = villager.getResidency().getHomeVillage();
        if (home.isPresent() && home.get().getId() == village.getId()) return true;

        UUID id = villager.getUUID();
        return village.getResidentsUUIDs().anyMatch(id::equals);
    }

    public static int totalCookSlots(Village village) {
        return buildKitchenSlots(village).size();
    }

    public static int highestKitchenTier(Village village) {
        int best = 0;
        for (Building building : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            String type = building.getType();
            if (!isKitchenType(type)) continue;
            best = Math.max(best, kitchenTierFromType(type));
        }
        return best;
    }

    public static int effectiveKitchenTier(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Building> kitchen = assignedKitchen(level, villager);
        if (kitchen.isPresent()) {
            return Math.max(0, kitchenTierFromType(kitchen.get().getType()));
        }
        Optional<Village> village = resolveVillage(villager);
        return village.map(FarmersDelightCookAssignment::highestKitchenTier).orElse(0);
    }

    /**
     * Returns the effective recipe tier for this cook, based on the kitchen
     * building tier. Cook personal progression no longer gates recipes.
     */
    public static int effectiveRecipeTier(ServerLevel level, VillagerEntityMCA villager) {
        return effectiveKitchenTier(level, villager);
    }

    /**
     * The cook site this villager works: a kitchen slot, or — once kitchen slots run out — a
     * standalone outdoor post (a declared via-surface POI standing outside every kitchen).
     * Sites and cooks are both deterministically ordered, so assignment is stable across
     * callers and ticks.
     */
    public static Optional<CookSite> assignedCookSite(ServerLevel level, VillagerEntityMCA villager) {
        Optional<Village> villageOpt = resolveVillage(villager);
        if (villageOpt.isEmpty()) return Optional.empty();
        Village village = villageOpt.get();
        if (!isEligibleVillageMember(village, villager)) return Optional.empty();

        List<CookSite> sites = buildCookSites(level, village);
        if (sites.isEmpty()) return Optional.empty();

        List<VillagerEntityMCA> cooks = sortedCookResidents(level, village);
        if (declaresCookWork(villager.getVillagerData().getProfession())) {
            boolean present = cooks.stream().anyMatch(v -> v.getUUID().equals(villager.getUUID()));
            if (!present) {
                cooks.add(villager);
                cooks.sort(Comparator.comparing(v -> v.getUUID().toString()));
            }
        }
        int idx = -1;
        for (int i = 0; i < cooks.size(); i++) {
            if (cooks.get(i).getUUID().equals(villager.getUUID())) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= sites.size()) return Optional.empty();
        return Optional.of(sites.get(idx));
    }

    public static Optional<Building> assignedKitchen(ServerLevel level, VillagerEntityMCA villager) {
        return assignedCookSite(level, villager).map(CookSite::building).filter(java.util.Objects::nonNull);
    }

    public static Set<Long> assignedKitchenBounds(ServerLevel level, VillagerEntityMCA villager) {
        Optional<CookSite> site = assignedCookSite(level, villager);
        if (site.isEmpty()) return Set.of();
        // The walkable room discovered from the world, not MCA's furniture-only geometry:
        // standable floor, stands, and arrival detection all derive from this set. An outdoor
        // post anchors the same flood-fill on its workstation block.
        Building kitchen = site.get().building();
        if (kitchen != null) {
            return com.aetherianartificer.townstead.work.WorkSiteBounds.workArea(level, kitchen);
        }
        return com.aetherianartificer.townstead.work.WorkSiteBounds.workAreaAround(level, site.get().post());
    }

    /**
     * PathAffinity worksite probe: whether the villager's assigned cook worksite contains any
     * of these blocks. Runs on tier-up only (auto-spend), so a bounds scan is fine.
     */
    public static boolean worksiteContainsAny(ServerLevel level, VillagerEntityMCA villager,
                                              List<ResourceLocation> blockIds) {
        for (long packed : assignedKitchenBounds(level, villager)) {
            BlockPos pos = BlockPos.of(packed);
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock());
            if (blockIds.contains(id)) return true;
        }
        return false;
    }

    public static boolean isKitchenType(String buildingTypeId) {
        return CookTierRules.isKitchenType(buildingTypeId);
    }

    static int kitchenTierFromType(String buildingTypeId) {
        return CookTierRules.kitchenTierFromType(buildingTypeId);
    }

    static int slotsForKitchenType(String buildingTypeId) {
        return CookTierRules.slotsForKitchenType(buildingTypeId);
    }

    private static List<Building> sortedKitchens(Village village) {
        List<Building> kitchens = new ArrayList<>();
        for (Building building : com.aetherianartificer.townstead.compat.mca.McaBuildings.all(village)) {
            if (!isKitchenType(building.getType())) continue;
            kitchens.add(building);
        }
        kitchens.sort((a, b) -> {
            BlockPos ac = a.getCenter();
            BlockPos bc = b.getCenter();
            if (ac != null && bc != null) {
                if (ac.getY() != bc.getY()) return Integer.compare(ac.getY(), bc.getY());
                if (ac.getZ() != bc.getZ()) return Integer.compare(ac.getZ(), bc.getZ());
                if (ac.getX() != bc.getX()) return Integer.compare(ac.getX(), bc.getX());
            } else if (ac != null) {
                return -1;
            } else if (bc != null) {
                return 1;
            }
            return a.getType().compareTo(b.getType());
        });
        return kitchens;
    }

    private static List<VillagerEntityMCA> sortedCookResidents(ServerLevel level, Village village) {
        List<VillagerEntityMCA> cooks = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (VillagerEntityMCA resident : village.getResidents(level)) {
            if (!declaresCookWork(resident.getVillagerData().getProfession())) continue;
            if (!seen.add(resident.getUUID())) continue;
            cooks.add(resident);
        }
        cooks.sort(Comparator.comparing(v -> v.getUUID().toString()));
        return cooks;
    }

    private static List<KitchenSlot> buildKitchenSlots(Village village) {
        List<KitchenSlot> slots = new ArrayList<>();
        for (Building kitchen : sortedKitchens(village)) {
            int tier = kitchenTierFromType(kitchen.getType());
            int slotCount = slotsForTier(tier);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new KitchenSlot(kitchen, i));
            }
        }
        return slots;
    }

    /** Kitchen slots first, then outdoor posts — the same order capacity counts them. */
    private static List<CookSite> buildCookSites(ServerLevel level, Village village) {
        List<CookSite> sites = new ArrayList<>();
        for (KitchenSlot slot : buildKitchenSlots(village)) {
            sites.add(new CookSite(slot.building(), null));
        }
        com.aetherianartificer.townstead.profession.def.ProfessionDef def = cookDef();
        if (def != null) {
            for (BlockPos post : com.aetherianartificer.townstead.profession.ProfessionCapacity
                    .standalonePois(level, village, def)) {
                sites.add(new CookSite(null, post));
            }
        }
        return sites;
    }

    /** The def whose work tasks declare cook work: the career the cook site machinery serves. */
    private static com.aetherianartificer.townstead.profession.def.ProfessionDef cookDef() {
        for (com.aetherianartificer.townstead.profession.def.ProfessionDef def
                : com.aetherianartificer.townstead.profession.def.ProfessionDefs.all().values()) {
            for (com.aetherianartificer.townstead.profession.def.WorkTaskDef task : def.workTasks()) {
                if (task.type().equals(com.aetherianartificer.townstead.profession.def.WorkTaskTypes.COOK)) {
                    return def;
                }
            }
        }
        return null;
    }

    /** One unit of cook employment: a kitchen (building non-null) or an outdoor post. */
    public record CookSite(@javax.annotation.Nullable Building building,
                           @javax.annotation.Nullable BlockPos post) {}

    private record KitchenSlot(Building building, int ordinal) {}

    static int slotsForTier(int tier) {
        return CookTierRules.slotsForTier(tier);
    }
}
