package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure functions that turn a village's current building list into a
 * {@link SpiritTotals} (point aggregation) and then into a
 * {@link SpiritReadout} (classification + tier).
 *
 * Tier semantics are per-spirit: the ladder {@link #TIER_THRESHOLDS_INTERNAL}
 * measures how steeped the village is in a single spirit, not how big the
 * village is overall. A village with 30 Nautical points sits at Nautical
 * tier 1 ("Fishing Spot") regardless of how many houses it has.
 *
 * Classification rules in order:
 * <ol>
 *   <li>No spirit at tier 1 (nobody above the first threshold) → SETTLEMENT.</li>
 *   <li>Top spirit holds ≥ 40% share → SINGLE, tier = top spirit's own tier.</li>
 *   <li>Top two each hold ≥ 25% share → BLEND, tier = max of the two tiers.</li>
 *   <li>Otherwise → MIXED. Tier field encodes the spread level
 *       (1 Crossroads, 2 Metropolis, 3 Cosmopolis, 4 Convergence).</li>
 * </ol>
 *
 * Called on the server after building changes; also safely callable anywhere
 * the same view of the village exists, though the server-sent snapshot is the
 * canonical source.
 */
public final class VillageSpiritAggregator {
    /**
     * Per-spirit point thresholds to cross each tier, in ascending order.
     * Tier 1 through tier 5 (index 0..4). Tier 0 = below the first threshold
     * (no descriptive name earned yet for that spirit).
     */
    private static final int[] TIER_THRESHOLDS_INTERNAL = {25, 60, 140, 300, 600};
    /** One spirit's share of total points to qualify as the single dominant identity. */
    static final double SINGLE_DOMINANT_SHARE = 0.40;
    /** Per-spirit share floor for a two-spirit blend readout. */
    static final double BLEND_SHARE = 0.25;

    public static int[] tierThresholds() {
        return TIER_THRESHOLDS_INTERNAL.clone();
    }

    private VillageSpiritAggregator() {}

    public static SpiritTotals totalsFor(Village village) {
        if (village == null) return SpiritTotals.empty();
        long t0 = System.nanoTime();
        Map<String, Integer> perSpirit = new HashMap<>();
        int total = 0;
        int contributingBuildings = 0;
        int scanned = 0;
        int incomplete = 0;
        for (Building b : village.getBuildings().values()) {
            scanned++;
            if (!b.isComplete()) { incomplete++; continue; }
            Map<String, Integer> contributions = BuildingSpiritIndex.contributionsFor(b.getType());
            if (contributions.isEmpty()) continue;
            boolean anyAdded = false;
            for (Map.Entry<String, Integer> e : contributions.entrySet()) {
                String spirit = e.getKey();
                Integer pts = e.getValue();
                if (pts == null || pts <= 0) continue;
                if (!SpiritRegistry.contains(spirit)) continue;
                perSpirit.merge(spirit, pts, Integer::sum);
                total += pts;
                anyAdded = true;
            }
            if (anyAdded) contributingBuildings++;
        }
        long elapsed = System.nanoTime() - t0;
        Townstead.LOGGER.info("[TS-Diag/Spirit] totalsFor village={} scanned={} incomplete={} contributing={} totalPts={} elapsedUs={}",
                village.getId(), scanned, incomplete, contributingBuildings, total, elapsed / 1_000L);
        return new SpiritTotals(Map.copyOf(perSpirit), total, contributingBuildings);
    }

    /**
     * Per-spirit contributor breakdown: spirit id → list of (buildingType,
     * count, points) rows sorted by points descending. Computed in one pass
     * so callers that need both totals and contributors should prefer
     * {@link #snapshotFor}.
     */
    public static Map<String, List<ContributorRow>> contributorsFor(Village village) {
        if (village == null) return Map.of();
        Map<String, Map<String, int[]>> bySpirit = new HashMap<>();
        for (Building b : village.getBuildings().values()) {
            if (!b.isComplete()) continue;
            String type = b.getType();
            Map<String, Integer> contributions = BuildingSpiritIndex.contributionsFor(type);
            if (contributions.isEmpty()) continue;
            for (Map.Entry<String, Integer> e : contributions.entrySet()) {
                int pts = e.getValue() == null ? 0 : e.getValue();
                if (pts <= 0) continue;
                if (!SpiritRegistry.contains(e.getKey())) continue;
                int[] agg = bySpirit
                        .computeIfAbsent(e.getKey(), k -> new HashMap<>())
                        .computeIfAbsent(type, k -> new int[]{0, 0});
                agg[0]++;
                agg[1] += pts;
            }
        }
        Map<String, List<ContributorRow>> out = new HashMap<>();
        for (Map.Entry<String, Map<String, int[]>> spiritEntry : bySpirit.entrySet()) {
            List<ContributorRow> rows = new ArrayList<>(spiritEntry.getValue().size());
            for (Map.Entry<String, int[]> typeEntry : spiritEntry.getValue().entrySet()) {
                rows.add(new ContributorRow(typeEntry.getKey(),
                        typeEntry.getValue()[0], typeEntry.getValue()[1]));
            }
            rows.sort(Comparator.comparingInt(ContributorRow::points).reversed());
            out.put(spiritEntry.getKey(), List.copyOf(rows));
        }
        return Map.copyOf(out);
    }

    /** Combined snapshot: totals + per-spirit contributors in one pass. */
    public static record Snapshot(SpiritTotals totals, Map<String, List<ContributorRow>> contributors) {}

    public static Snapshot snapshotFor(Village village) {
        if (village == null) return new Snapshot(SpiritTotals.empty(), Map.of());
        long t0 = System.nanoTime();
        Map<String, Integer> perSpirit = new HashMap<>();
        Map<String, Map<String, int[]>> bySpirit = new HashMap<>();
        int total = 0;
        int contributingBuildings = 0;
        int scanned = 0;
        int incomplete = 0;
        for (Building b : village.getBuildings().values()) {
            scanned++;
            if (!b.isComplete()) { incomplete++; continue; }
            String type = b.getType();
            Map<String, Integer> contributions = BuildingSpiritIndex.contributionsFor(type);
            if (contributions.isEmpty()) continue;
            boolean anyAdded = false;
            for (Map.Entry<String, Integer> e : contributions.entrySet()) {
                int pts = e.getValue() == null ? 0 : e.getValue();
                if (pts <= 0) continue;
                if (!SpiritRegistry.contains(e.getKey())) continue;
                perSpirit.merge(e.getKey(), pts, Integer::sum);
                total += pts;
                anyAdded = true;
                int[] agg = bySpirit
                        .computeIfAbsent(e.getKey(), k -> new HashMap<>())
                        .computeIfAbsent(type, k -> new int[]{0, 0});
                agg[0]++;
                agg[1] += pts;
            }
            if (anyAdded) contributingBuildings++;
        }
        Map<String, List<ContributorRow>> contribOut = new HashMap<>();
        for (Map.Entry<String, Map<String, int[]>> spiritEntry : bySpirit.entrySet()) {
            List<ContributorRow> rows = new ArrayList<>(spiritEntry.getValue().size());
            for (Map.Entry<String, int[]> typeEntry : spiritEntry.getValue().entrySet()) {
                rows.add(new ContributorRow(typeEntry.getKey(),
                        typeEntry.getValue()[0], typeEntry.getValue()[1]));
            }
            rows.sort(Comparator.comparingInt(ContributorRow::points).reversed());
            contribOut.put(spiritEntry.getKey(), List.copyOf(rows));
        }
        SpiritTotals totals = new SpiritTotals(Map.copyOf(perSpirit), total, contributingBuildings);
        long elapsed = System.nanoTime() - t0;
        Townstead.LOGGER.info("[TS-Diag/Spirit] snapshotFor village={} scanned={} incomplete={} contributing={} totalPts={} contribGroups={} elapsedUs={}",
                village.getId(), scanned, incomplete, contributingBuildings,
                total, contribOut.size(), elapsed / 1_000L);
        return new Snapshot(totals, Map.copyOf(contribOut));
    }

    public static SpiritReadout readoutFor(SpiritTotals totals) {
        // Find top two spirits by points in registry order (deterministic tie-break).
        String top1 = null;
        int top1Pts = 0;
        String top2 = null;
        int top2Pts = 0;
        for (SpiritRegistry.Spirit s : SpiritRegistry.ordered()) {
            int pts = totals.pointsFor(s.id());
            if (pts <= 0) continue;
            if (pts > top1Pts) {
                top2 = top1;
                top2Pts = top1Pts;
                top1 = s.id();
                top1Pts = pts;
            } else if (pts > top2Pts) {
                top2 = s.id();
                top2Pts = pts;
            }
        }

        int top1Tier = tierForSpirit(top1Pts);
        int top2Tier = tierForSpirit(top2Pts);

        // No spirit has crossed the first threshold — pure Settlement.
        if (top1Tier < 1) {
            return new SpiritReadout(SpiritReadout.Classification.SETTLEMENT, 0, null, null);
        }

        double share1 = totals.total() > 0 ? top1Pts / (double) totals.total() : 0.0;
        double share2 = totals.total() > 0 ? top2Pts / (double) totals.total() : 0.0;

        if (share1 >= SINGLE_DOMINANT_SHARE) {
            return new SpiritReadout(SpiritReadout.Classification.SINGLE, top1Tier, top1, null);
        }
        if (top2 != null && share1 >= BLEND_SHARE && share2 >= BLEND_SHARE) {
            int blendTier = Math.max(top1Tier, top2Tier);
            // Canonical pair ordering: lower registry index first, so the lang
            // key is deterministic regardless of which spirit won by points.
            String primary = top1;
            String secondary = top2;
            if (SpiritRegistry.indexOf(top2) < SpiritRegistry.indexOf(top1)) {
                primary = top2;
                secondary = top1;
            }
            return new SpiritReadout(SpiritReadout.Classification.BLEND, blendTier, primary, secondary);
        }

        int spread = mixedSpreadLevel(totals);
        return new SpiritReadout(SpiritReadout.Classification.MIXED, spread, null, null);
    }

    /**
     * Per-spirit tier from that spirit's own point total. 0 means the spirit
     * has not crossed the first descriptive-name threshold yet.
     */
    public static int tierForSpirit(int spiritPoints) {
        int t = 0;
        for (int threshold : TIER_THRESHOLDS_INTERNAL) {
            if (spiritPoints >= threshold) t++;
            else break;
        }
        return t;
    }

    /**
     * For MIXED villages, classify the breadth of spirit presence. Higher
     * levels stand in for the lost single-spirit "grandeur" tiers — a village
     * without a dominant identity earns recognition for having many spirits
     * all at meaningful depth.
     *
     * <ul>
     *   <li>4 — Convergence: every registered spirit at tier 3+.</li>
     *   <li>3 — Cosmopolis: ≥ 3 spirits at tier 4+.</li>
     *   <li>2 — Metropolis: ≥ 3 spirits at tier 3+.</li>
     *   <li>1 — Crossroads: otherwise (there's at least one spirit at tier 1
     *       since this method is only reached when top1Tier ≥ 1).</li>
     * </ul>
     */
    static int mixedSpreadLevel(SpiritTotals totals) {
        int atTier2OrMore = 0;
        int atTier3OrMore = 0;
        int atTier4OrMore = 0;
        int totalSpirits = SpiritRegistry.ordered().size();
        int spiritsAtTier3OrMore = 0;
        for (SpiritRegistry.Spirit s : SpiritRegistry.ordered()) {
            int tier = tierForSpirit(totals.pointsFor(s.id()));
            if (tier >= 2) atTier2OrMore++;
            if (tier >= 3) {
                atTier3OrMore++;
                spiritsAtTier3OrMore++;
            }
            if (tier >= 4) atTier4OrMore++;
        }
        if (spiritsAtTier3OrMore == totalSpirits) return 4;
        if (atTier4OrMore >= 3) return 3;
        if (atTier3OrMore >= 3) return 2;
        return 1;
    }
}
