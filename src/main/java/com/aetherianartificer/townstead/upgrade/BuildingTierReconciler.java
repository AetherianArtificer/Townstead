package com.aetherianartificer.townstead.upgrade;

import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.recognition.SiteRequirements;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Reconciles forced Townstead tier families using MCA's own room matching context. */
public final class BuildingTierReconciler {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/TierReconciler");

    private BuildingTierReconciler() {}

    public static void reconcileVillage(Village village, ServerLevel level) {
        if (village == null) return;
        for (Building building : McaBuildings.all(village)) reconcileBuilding(village, level, building);
    }

    private static void reconcileBuilding(Village village, ServerLevel level, Building building) {
        if (building == null) return;
        String currentType = building.getType();
        if (currentType == null) return;

        // MCA owns the recorded POIs, room inheritance, tag expansion, and block-count matching.
        // Townstead only chooses the highest satisfied member of this room's declared tier family.
        List<String> matching = McaBuildingCompat.matchingTypeNames(village, building);
        if (McaBuildings.isOpenAirRecord(level, village, building)) {
            matching = meetingSiteRequirements(level, building, matching);
            if (matching == null) return;
        }
        String best = BuildingCandidatePolicy.highestMatchingTierInFamily(currentType, matching);
        if (best == null) {
            // A declared tier family with no currently satisfied member must fall back to MCA's
            // direct type determination. Forced manual selections are released first.
            if (com.aetherianartificer.townstead.client.catalog.CatalogDataLoader
                    .matchGroup(currentType).filter(g -> "tiered".equalsIgnoreCase(g.layout())).isPresent()) {
                building.setTypeForced(false);
                building.determineType();
                LOG.debug("Tier '{}' is no longer satisfied; MCA resolved room {} as '{}'",
                        currentType, building.getId(), building.getType());
            }
            return;
        }
        if (!best.equals(currentType)) {
            building.setType(best);
            LOG.debug("MCA matching reconciled room {} from '{}' to '{}'", building.getId(), currentType, best);
        }
    }

    /**
     * MCA counts a tier's furniture; the site it stands on (a wharf's deck over water) is
     * Townstead's to check. Null means the site is not loaded, so nothing can be concluded.
     */
    private static List<String> meetingSiteRequirements(
            ServerLevel level, Building building, List<String> matching) {
        if (level == null || matching.stream().allMatch(name -> SiteRequirements.of(name).isEmpty())) {
            return matching;
        }
        List<BlockPos> seeds = building.getBlockPosStream().toList();
        BlockPos min = building.getPos0();
        BlockPos max = building.getPos1();
        int radius = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2 + 2;
        List<String> kept = new ArrayList<>();
        for (String name : matching) {
            SiteRequirements.Verdict verdict = SiteRequirements.evaluate(
                    level, name, seeds, building.getCenter(), radius).verdict();
            if (verdict == SiteRequirements.Verdict.UNKNOWN) return null;
            if (verdict == SiteRequirements.Verdict.SATISFIED) kept.add(name);
        }
        return kept;
    }
}
