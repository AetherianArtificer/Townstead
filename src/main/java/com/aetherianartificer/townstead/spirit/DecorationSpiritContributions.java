package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.decoration.Decorations;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.*;
import java.util.function.Function;

/** Adds built decorations to the same totals and contributor list as buildings. */
public final class DecorationSpiritContributions {
    private static final String PREFIX = "decoration/";
    private DecorationSpiritContributions() {}

    public static ResourceLocation decorationId(String contributor) {
        return contributor.startsWith(PREFIX) ? ResourceLocation.tryParse(contributor.substring(PREFIX.length())) : null;
    }

    public static VillageSpiritAggregator.Snapshot addTo(VillageSpiritAggregator.Snapshot buildings,
                                                          ServerLevel level, Village village) {
        return addTo(buildings, Decorations.countsForVillage(level, village), id -> {
            var definition = Decorations.definition(id);
            return definition == null ? Map.of() : definition.spirits();
        });
    }

    public static VillageSpiritAggregator.Snapshot addTo(VillageSpiritAggregator.Snapshot buildings,
            Map<ResourceLocation, Integer> counts, Function<ResourceLocation, Map<String, Integer>> contributions) {
        Map<String, Integer> points = new HashMap<>(buildings.totals().perSpirit());
        Map<String, List<ContributorRow>> rows = new HashMap<>();
        buildings.contributors().forEach((spirit, existing) -> rows.put(spirit, new ArrayList<>(existing)));
        int total = buildings.totals().total();
        for (var decoration : counts.entrySet()) {
            int count = decoration.getValue();
            if (count <= 0) continue;
            for (var contribution : contributions.apply(decoration.getKey()).entrySet()) {
                if (!SpiritRegistry.contains(contribution.getKey()) || contribution.getValue() <= 0) continue;
                int added = count * contribution.getValue();
                points.merge(contribution.getKey(), added, Integer::sum);
                total += added;
                rows.computeIfAbsent(contribution.getKey(), ignored -> new ArrayList<>())
                        .add(new ContributorRow(PREFIX + decoration.getKey(), count, added));
            }
        }
        Map<String, List<ContributorRow>> sorted = new HashMap<>();
        rows.forEach((spirit, values) -> sorted.put(spirit, values.stream()
                .sorted(Comparator.comparingInt(ContributorRow::points).reversed().thenComparing(ContributorRow::buildingType)).toList()));
        return new VillageSpiritAggregator.Snapshot(new SpiritTotals(Map.copyOf(points), total,
                buildings.totals().contributingBuildings()), Map.copyOf(sorted));
    }
}
