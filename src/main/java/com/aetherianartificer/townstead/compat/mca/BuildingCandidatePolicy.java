package com.aetherianartificer.townstead.compat.mca;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import net.conczin.mca.resources.data.BuildingType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Townstead policy applied after MCA has performed authoritative room matching.
 *
 * <p>MCA deliberately reports every satisfied type. Tier definitions are cumulative, so a level
 * five kitchen also satisfies one or more lower kitchen definitions and would otherwise be treated
 * as an ambiguous room. Townstead only resolves that artificial ambiguity: members of one catalog
 * group whose layout is {@code tiered} collapse to the highest numeric {@code _lN} member. Real
 * ambiguity between different families is preserved for MCA's chooser.</p>
 */
public final class BuildingCandidatePolicy {
    private BuildingCandidatePolicy() {}

    public static List<BuildingType> normalizeForRecognition(Collection<BuildingType> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        Map<String, BuildingType> byName = new LinkedHashMap<>();
        for (BuildingType candidate : candidates) {
            if (candidate != null) byName.putIfAbsent(candidate.name(), candidate);
        }
        List<String> names = normalizeNamesForRecognition(byName.keySet());
        return names.stream().map(byName::get).filter(java.util.Objects::nonNull).toList();
    }

    public static List<String> normalizeNamesForRecognition(Collection<String> candidates) {
        List<String> permitted = CatalogDataLoader
                .withoutActiveSupersededBuildingTypesForRecognition(candidates);
        return collapseTierFamilies(permitted, CatalogDataLoader.groups());
    }

    /** Highest currently satisfied member of the same declared tier family as {@code current}. */
    public static String highestMatchingTierInFamily(
            String current, Collection<String> matchingCandidates) {
        if (current == null || matchingCandidates == null || matchingCandidates.isEmpty()) return null;
        CatalogDataLoader.GroupDef family = bestTierGroup(current, CatalogDataLoader.groups());
        if (family == null) return null;
        return matchingCandidates.stream()
                .filter(name -> {
                    CatalogDataLoader.GroupDef group = bestTierGroup(name, CatalogDataLoader.groups());
                    return group != null && family.id().equals(group.id());
                })
                .filter(name -> tierOf(name) >= 0)
                .max(Comparator.comparingInt(BuildingCandidatePolicy::tierOf))
                .orElse(null);
    }

    /** Package-visible for regression tests without loading MCA registries. */
    static List<String> collapseTierFamilies(
            Collection<String> candidates,
            Collection<CatalogDataLoader.GroupDef> groups) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (groups == null || groups.isEmpty()) return List.copyOf(candidates);

        Map<String, String> candidateGroup = new HashMap<>();
        Map<String, List<String>> membersByGroup = new LinkedHashMap<>();
        for (String candidate : candidates) {
            if (candidate == null) continue;
            CatalogDataLoader.GroupDef group = bestTierGroup(candidate, groups);
            if (group == null) continue;
            candidateGroup.put(candidate, group.id());
            membersByGroup.computeIfAbsent(group.id(), ignored -> new ArrayList<>()).add(candidate);
        }

        Map<String, String> winnerByGroup = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : membersByGroup.entrySet()) {
            List<String> members = entry.getValue();
            if (members.size() < 2) continue;
            // Only collapse when every competing member declares a tier. A malformed or mixed
            // family must stay ambiguous rather than being guessed at.
            if (members.stream().anyMatch(name -> tierOf(name) < 0)) continue;
            String winner = members.stream()
                    .max(Comparator.comparingInt(BuildingCandidatePolicy::tierOf))
                    .orElse(null);
            if (winner != null) winnerByGroup.put(entry.getKey(), winner);
        }
        if (winnerByGroup.isEmpty()) return List.copyOf(candidates);

        List<String> result = new ArrayList<>();
        Set<String> emittedGroups = new HashSet<>();
        for (String candidate : candidates) {
            String groupId = candidateGroup.get(candidate);
            String winner = groupId == null ? null : winnerByGroup.get(groupId);
            if (winner == null) {
                result.add(candidate);
            } else if (emittedGroups.add(groupId)) {
                result.add(winner);
            }
        }
        return List.copyOf(result);
    }

    private static CatalogDataLoader.GroupDef bestTierGroup(
            String candidate,
            Collection<CatalogDataLoader.GroupDef> groups) {
        CatalogDataLoader.GroupDef best = null;
        for (CatalogDataLoader.GroupDef group : groups) {
            if (!"tiered".equalsIgnoreCase(group.layout()) || group.matchPrefix().isEmpty()
                    || !candidate.startsWith(group.matchPrefix())) continue;
            if (best == null || group.priority() > best.priority()
                    || group.priority() == best.priority()
                    && group.matchPrefix().length() > best.matchPrefix().length()) {
                best = group;
            }
        }
        return best;
    }

    static int tierOf(String typeName) {
        if (typeName == null) return -1;
        int marker = typeName.lastIndexOf("_l");
        if (marker < 0 || marker + 2 >= typeName.length()) return -1;
        String suffix = typeName.substring(marker + 2);
        if (suffix.isEmpty() || !suffix.chars().allMatch(Character::isDigit)) return -1;
        try {
            return Integer.parseInt(suffix);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
