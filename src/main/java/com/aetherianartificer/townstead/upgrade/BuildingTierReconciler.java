package com.aetherianartificer.townstead.upgrade;

import com.aetherianartificer.townstead.compat.mca.BuildingCandidatePolicy;
import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.compat.mca.McaBuildings;
import com.aetherianartificer.townstead.recognition.BuildingChecks;
import com.aetherianartificer.townstead.recognition.SiteRequirements;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Reconciles forced Townstead tier families using MCA's own room matching context. */
public final class BuildingTierReconciler {
    private static final Logger LOG = LoggerFactory.getLogger("Townstead/TierReconciler");
    private static final String GENERIC_TYPE = "building";

    private BuildingTierReconciler() {}

    /** Returns the checks that turned a building away from a type MCA matched. */
    public static List<BuildingChecks.Failure> reconcileVillage(Village village, ServerLevel level) {
        if (village == null) return List.of();
        List<BuildingChecks.Failure> failures = new ArrayList<>();
        for (Building building : McaBuildings.all(village)) {
            reconcileBuilding(village, level, building);
            BuildingChecks.Failure failure = applyChecks(village, level, building);
            if (failure != null) failures.add(failure);
        }
        return failures;
    }

    /**
     * MCA matches a type from its block recipe; Townstead's checks (a decoration inside, the floor
     * area, the height) then decide among MCA's own candidates. A type that fails gives way to the
     * next candidate that passes, or to MCA's generic building. A candidate MCA ranked ahead of
     * the current type takes over once it passes, unless the player chose the current type.
     * Buildings whose candidates declare no checks are left to MCA. Returns the near miss to tell
     * the player about: the first candidate ahead of the result that failed a check.
     */
    public static @Nullable BuildingChecks.Failure applyChecks(Village village, ServerLevel level, Building building) {
        if (level == null || building == null || building.getType() == null) return null;
        String current = building.getType();
        List<String> matching = McaBuildingCompat.candidateTypeNames(village, building);
        if (BuildingChecks.of(current).isEmpty() && !hasChecks(matching)) return null;
        BuildingChecks.Failure nearMiss = BuildingChecks.failure(level, village, building, current);
        String chosen = current;
        if (nearMiss != null) {
            chosen = GENERIC_TYPE;
            for (String candidate : matching) {
                if (!candidate.equals(current) && BuildingChecks.failure(level, village, building, candidate) == null) {
                    chosen = candidate;
                    break;
                }
            }
        } else {
            int currentIndex = matching.indexOf(current);
            for (int i = 0; i < currentIndex; i++) {
                String candidate = matching.get(i);
                if (BuildingChecks.of(candidate).isEmpty()) continue;
                BuildingChecks.Failure failure = BuildingChecks.failure(level, village, building, candidate);
                if (failure == null && !building.isTypeForced()) {
                    chosen = candidate;
                    break;
                }
                if (failure != null && nearMiss == null) nearMiss = failure;
            }
        }
        if (!chosen.equals(current)) {
            building.setTypeForced(false);
            building.setType(chosen);
            LOG.debug("Townstead checks moved room {} from '{}' to '{}'", building.getId(), current, chosen);
        }
        return nearMiss;
    }

    private static boolean hasChecks(List<String> names) {
        for (String name : names) if (!BuildingChecks.of(name).isEmpty()) return true;
        return false;
    }

    private static void reconcileBuilding(Village village, ServerLevel level, Building building) {
        if (building == null) return;
        String currentType = building.getType();
        if (currentType == null) return;

        // MCA owns the recorded POIs, room inheritance, tag expansion, and block-count matching.
        // Townstead only chooses the highest satisfied member of this room's declared tier family.
        List<String> matching = McaBuildingCompat.matchingTypeNames(village, building);
        if (level != null && hasChecks(McaBuildingCompat.candidateTypeNames(village, building))) {
            // A tier that fails its checks must not be chosen; the family falls back to a lower tier.
            matching = McaBuildingCompat.candidateTypeNames(village, building).stream()
                    .filter(name -> BuildingChecks.failure(level, village, building, name) == null).toList();
        }
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
