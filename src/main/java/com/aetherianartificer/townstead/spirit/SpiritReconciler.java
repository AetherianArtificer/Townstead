package com.aetherianartificer.townstead.spirit;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.recognition.RecognitionEffects;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Recomputes a village's spirit totals + readout and — if the structural
 * readout has meaningfully changed since the last cached value — fires a
 * recognition effect + action-bar announcement.
 *
 * Follows the same "seed silently, diff on reconcile" pattern as
 * {@code BuildingRecognitionTracker}: the first time a village is seen, we
 * just populate the cache; subsequent calls compare against it.
 *
 * Call sites are the same as the existing recognition tracker's — any place
 * where a village's buildings may have changed:
 * <ul>
 *   <li>{@code ReportBuildingMessageMixin} (player ADD/ADD_ROOM/REMOVE/FULL_SCAN)</li>
 *   <li>{@code DockBuildingSync.sync} (runs during blueprint refresh near a dock shape)</li>
 * </ul>
 */
public final class SpiritReconciler {
    private static final Logger LOG = LoggerFactory.getLogger(Townstead.MOD_ID + "/SpiritReconciler");
    private static final double ANNOUNCE_RADIUS = 96.0;

    private SpiritReconciler() {}

    /**
     * Seed the cache silently with the current village state. Used at server
     * start so the first real reconcile call doesn't fire tier-change events
     * for every village that existed before this session.
     */
    public static void seed(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        long t0 = System.nanoTime();
        VillageSpiritAggregator.Snapshot snap = VillageSpiritAggregator.snapshotFor(village);
        long t1 = System.nanoTime();
        SpiritReadout readout = VillageSpiritAggregator.readoutFor(snap.totals());
        long t2 = System.nanoTime();
        VillageSpiritCache.put(level, village.getId(),
                new VillageSpiritCache.Entry(snap.totals(), readout, snap.contributors()));
        long t3 = System.nanoTime();
        Townstead.LOGGER.info("[TS-Diag/Spirit] seed village={} buildings={} snapshotUs={} readoutUs={} putUs={} totalPts={} contributing={}",
                village.getId(), village.getBuildings().size(),
                (t1 - t0) / 1_000L, (t2 - t1) / 1_000L, (t3 - t2) / 1_000L,
                snap.totals().total(), snap.totals().contributingBuildings());
    }

    /**
     * Recompute totals + readout for the village and compare against the
     * last cached value. If the readout's structural shape changed (tier,
     * classification, or dominant spirit), fire a celebratory recognition
     * effect centered on the village + broadcast an action-bar announcement
     * to nearby players. Always updates the cache.
     */
    public static void reconcileVillage(ServerLevel level, Village village) {
        if (level == null || village == null) return;
        long t0 = System.nanoTime();
        VillageSpiritAggregator.Snapshot snap = VillageSpiritAggregator.snapshotFor(village);
        long t1 = System.nanoTime();
        SpiritReadout newReadout = VillageSpiritAggregator.readoutFor(snap.totals());
        long t2 = System.nanoTime();
        VillageSpiritCache.Entry prev = VillageSpiritCache.get(level, village.getId());
        VillageSpiritCache.put(level, village.getId(),
                new VillageSpiritCache.Entry(snap.totals(), newReadout, snap.contributors()));
        long t3 = System.nanoTime();
        boolean structural = prev != null && newReadout.isStructuralChange(prev.readout());
        Townstead.LOGGER.info("[TS-Diag/Spirit] reconcile village={} buildings={} snapshotUs={} readoutUs={} cacheUs={} totalUs={} prevCached={} structuralChange={}",
                village.getId(), village.getBuildings().size(),
                (t1 - t0) / 1_000L, (t2 - t1) / 1_000L, (t3 - t2) / 1_000L,
                (t3 - t0) / 1_000L, prev != null, structural);
        if (prev == null) {
            return; // first observation, no event
        }
        if (!structural) {
            return; // readout unchanged
        }
        fireTierChange(level, village, prev.readout(), newReadout);
    }

    private static void fireTierChange(ServerLevel level, Village village,
                                       SpiritReadout prev, SpiritReadout next) {
        BoundingBox bounds = villageBounds(village);
        RecognitionEffects.Tier effectTier = effectTierFor(next);
        RecognitionEffects.playArea(level, bounds, effectTier, accentColor(next));
        Vec3 center = new Vec3(
                (bounds.minX() + bounds.maxX() + 1) / 2.0,
                (bounds.minY() + bounds.maxY() + 1) / 2.0,
                (bounds.minZ() + bounds.maxZ() + 1) / 2.0);
        MutableComponent message = Component.translatable(
                "townstead.spirit.tier_change", village.getName(), next.asComponent())
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        RecognitionEffects.announce(level, center, message, ANNOUNCE_RADIUS);
        LOG.info("[SpiritReconciler] village {} readout {} -> {}",
                village.getId(),
                describe(prev), describe(next));
    }

    private static BoundingBox villageBounds(Village village) {
        // Village#getBox returns BlockBoxExtended which extends vanilla BoundingBox,
        // so it's usable directly wherever BoundingBox is expected.
        return village.getBox();
    }

    /**
     * Map the new readout to a recognition intensity. Tier semantics differ
     * per classification so the mapping does too:
     *
     * <ul>
     *   <li>SETTLEMENT → MINOR (first steps out of Outpost are quiet).</li>
     *   <li>SINGLE / BLEND at tier 5 → GRAND (Bastion / Thalassocracy / etc.).</li>
     *   <li>MIXED at spread 3+ (Cosmopolis / Convergence) → GRAND.</li>
     *   <li>Tier 3+ (SINGLE/BLEND) or spread 2 (Metropolis) → MAJOR.</li>
     *   <li>Everything else → MINOR.</li>
     * </ul>
     */
    private static RecognitionEffects.Tier effectTierFor(SpiritReadout r) {
        SpiritReadout.Classification cls = r.classification();
        if (cls == SpiritReadout.Classification.SETTLEMENT) {
            return RecognitionEffects.Tier.MINOR;
        }
        if (cls == SpiritReadout.Classification.MIXED) {
            if (r.tierIndex() >= 3) return RecognitionEffects.Tier.GRAND;
            if (r.tierIndex() >= 2) return RecognitionEffects.Tier.MAJOR;
            return RecognitionEffects.Tier.MINOR;
        }
        // SINGLE or BLEND
        if (r.tierIndex() >= 5) return RecognitionEffects.Tier.GRAND;
        if (r.tierIndex() >= 3) return RecognitionEffects.Tier.MAJOR;
        return RecognitionEffects.Tier.MINOR;
    }

    /**
     * Color accent for the recognition particle tint. Uses the dominant
     * spirit's color when one exists; falls back to a gold-ish neutral for
     * MIXED / SETTLEMENT readouts so there's still a color in play.
     */
    private static int accentColor(SpiritReadout r) {
        if (r.primarySpiritId() != null) {
            var spirit = SpiritRegistry.get(r.primarySpiritId());
            if (spirit.isPresent()) return spirit.get().color();
        }
        return 0xFFE3D18A; // neutral warm accent
    }

    private static String describe(SpiritReadout r) {
        return r.classification().name() + "/" + r.tierIndex()
                + (r.primarySpiritId() != null ? "/" + r.primarySpiritId() : "")
                + (r.secondarySpiritId() != null ? "+" + r.secondarySpiritId() : "");
    }

}
