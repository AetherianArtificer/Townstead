package com.aetherianartificer.townstead.profession;

import com.aetherianartificer.townstead.profession.def.ProfessionDef;
import com.aetherianartificer.townstead.profession.def.ProfessionDefs;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Foreign professions that carry a career whose seats Townstead owns.
 *
 * <p>A compatibility document says "this registered profession IS the Cook career" (or one of
 * its Paths). A career with building seats is hired by those seats, so its carriers must be too:
 * otherwise the carrier's own job block hires by the vanilla one-block-one-worker rule and a
 * one-seat kitchen fills with as many chefs as it has skillets. While the career is enabled,
 * carriers' job sites are not acquirable through the brain and the seat resolver keeps or
 * releases carriers exactly as it does the career's own profession. Switch the career off and
 * the carrier mod hires by block again, untouched.</p>
 */
public final class ProfessionCarriers {

    private record Snapshot(Map<ResourceLocation, ProfessionDef> defs, boolean cookEnabled,
                            List<VillagerProfession> carriers,
                            java.util.Set<VillagerProfession> seatHired) {}

    private static volatile Snapshot snapshot;

    private ProfessionCarriers() {}

    /**
     * The seated career this profession id carries, or null when the id is a career of its own,
     * resolves to nothing, or resolves to a career whose hiring Townstead does not own.
     * Enablement is deliberately not consulted here so the rule stays testable.
     */
    @Nullable
    public static ProfessionDef carriedCareer(@Nullable ResourceLocation professionId) {
        if (professionId == null) return null;
        ResourceLocation canonical = ProfessionDefs.canonicalId(professionId);
        if (canonical == null || canonical.equals(professionId)) return null;
        ProfessionDef def = ProfessionDefs.all().get(canonical);
        return ProfessionAutoAssign.managesDefinition(def) ? def : null;
    }

    /** Whether this registered profession carries {@code def} rather than being {@code def} itself. */
    public static boolean carries(@Nullable VillagerProfession profession, @Nullable ProfessionDef def) {
        if (profession == null || def == null || profession == VillagerProfession.NONE) return false;
        ProfessionDef carried = carriedCareer(BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession));
        return carried != null && carried.id().equals(def.id());
    }

    /** Whether a brain hunting for work must skip this point of interest because seats hire it. */
    public static boolean ownsJobSite(Holder<PoiType> poi) {
        for (VillagerProfession carrier : carriers()) {
            if (carrier.heldJobSite().test(poi)) return true;
        }
        return false;
    }

    /** {@code original}, minus every job site whose hiring belongs to a seated career. */
    public static Predicate<Holder<PoiType>> excludingOwned(Predicate<Holder<PoiType>> original) {
        return poi -> original.test(poi) && !ownsJobSite(poi);
    }

    /**
     * Registered carriers of enabled, seated careers. Recomputed when the def registry is
     * replaced or the cook toggle flips; both are compared by identity and value, so the hot
     * path costs two comparisons and a walk over a list of one or two professions.
     */
    private static List<VillagerProfession> carriers() {
        return snapshot().carriers();
    }

    /**
     * Whether seats, not a job block, hire and release this profession: an enabled seated
     * career's own profession, a carrier of it, or a proprietor one of its buildings reserves a
     * seat for. A seat leaves no job-site memory, so nothing else may read that memory's absence
     * as unemployment.
     */
    public static boolean seatHired(@Nullable VillagerProfession profession) {
        return profession != null && snapshot().seatHired().contains(profession);
    }

    /** The profession ids {@link #seatHired} answers for under these definitions. */
    static java.util.Set<ResourceLocation> seatHiredIds(
            Map<ResourceLocation, ProfessionDef> defs,
            java.util.Set<ResourceLocation> compatibilityIds,
            java.util.function.Predicate<ProfessionDef> enabled) {
        java.util.Set<ResourceLocation> ids = new java.util.HashSet<>();
        for (ProfessionDef def : defs.values()) {
            if (!ProfessionAutoAssign.managesDefinition(def) || !enabled.test(def)) continue;
            ids.add(def.id());
            for (com.aetherianartificer.townstead.profession.def.JobSiteProvider provider
                    : def.jobSites()) {
                if (provider instanceof com.aetherianartificer.townstead.profession.def
                        .JobSiteProvider.Building building) {
                    ids.addAll(building.proprietor().professions());
                }
            }
        }
        for (ResourceLocation id : compatibilityIds) {
            ProfessionDef def = carriedCareer(id);
            if (def != null && enabled.test(def)) ids.add(id);
        }
        return ids;
    }

    private static Snapshot snapshot() {
        Map<ResourceLocation, ProfessionDef> defs = ProfessionDefs.all();
        boolean cookEnabled = com.aetherianartificer.townstead.TownsteadConfig.isTownsteadCookEnabled();
        Snapshot current = snapshot;
        if (current != null && current.defs() == defs && current.cookEnabled() == cookEnabled) {
            return current;
        }
        java.util.Set<VillagerProfession> seatHired = new java.util.HashSet<>();
        for (ResourceLocation id : seatHiredIds(defs, ProfessionDefs.compatibility().keySet(),
                ProfessionAutoAssign::enabled)) {
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION
                    .getOptional(id).orElse(null);
            if (profession != null && profession != VillagerProfession.NONE) seatHired.add(profession);
        }
        List<VillagerProfession> carriers = new ArrayList<>();
        // A native career can itself declare grouped job blocks. Exclude its raw POI from
        // vanilla hiring too, otherwise each regulator still creates an additional worker.
        for (ProfessionDef def : defs.values()) {
            if (!ProfessionAutoAssign.managesDefinition(def) || !ProfessionAutoAssign.enabled(def)) continue;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION
                    .getOptional(def.id()).orElse(null);
            if (profession != null && profession != VillagerProfession.NONE
                    && profession.heldJobSite() != PoiType.NONE) carriers.add(profession);
        }
        for (Map.Entry<ResourceLocation, ProfessionDefs.Resolution> entry
                : ProfessionDefs.compatibility().entrySet()) {
            ProfessionDef def = carriedCareer(entry.getKey());
            if (def == null || !ProfessionAutoAssign.enabled(def)) continue;
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION
                    .getOptional(entry.getKey()).orElse(null);
            if (profession == null || profession == VillagerProfession.NONE) continue;
            if (profession.heldJobSite() == PoiType.NONE) continue;
            carriers.add(profession);
        }
        Snapshot next = new Snapshot(defs, cookEnabled, List.copyOf(carriers),
                java.util.Set.copyOf(seatHired));
        snapshot = next;
        return next;
    }
}
