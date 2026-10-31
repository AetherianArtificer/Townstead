package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.client.root.RootCatalogClient;
import com.aetherianartificer.townstead.client.root.RootClientStore;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.root.GeneCatalogEntry;
import com.aetherianartificer.townstead.root.RootCatalogEntry;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Client-only easing of an entity's body render opacity. Each entity has a target at
 * any moment: an expressed {@code pheno:opacity} gene whose condition holds, or else
 * 0 when the (already client-synced) invisible flag is set and 1 otherwise. When the
 * target changes, render layers asking {@link #alpha} get a short gradient over
 * {@link #FADE_TICKS} instead of vanilla's hard cut. Opacity genes resolve like the
 * attachment layer's: the per-entity synced expressed set (which carries companions)
 * first, the origin-typical grant list as the pre-sync fallback, each against the
 * synced gene catalog — never the server-side power registry, which this client may
 * not have. Purely observational — no networking, no server state. Targets re-resolve
 * on the invisible-flag flip and on a short cadence, so slower gene conditions (time
 * of day, brightness) follow within a second.
 */
public final class InvisFade {

    private static final int FADE_TICKS = 8;
    private static final int TARGET_REFRESH_TICKS = 20;

    /** An ease from {@code from} toward {@code target} starting at {@code since}. */
    private record State(float from, float target, long since, boolean invisible, long resolvedAt) {}

    /** Seed marker: no transition observed yet, alpha sits at the target. */
    private static final long NEVER = Long.MIN_VALUE;

    private static final Map<Integer, State> STATES = new HashMap<>();
    /** Parsed opacity gates keyed by their catalog JSON (empty = declared but unparseable). */
    private static final Map<String, Optional<Condition>> CONDITIONS = new HashMap<>();

    private InvisFade() {}

    /** Once per client tick: drop entries for entities no longer in the level. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            clear();
            return;
        }
        STATES.keySet().removeIf(id -> mc.level.getEntity(id) == null);
    }

    public static void clear() {
        STATES.clear();
        CONDITIONS.clear();
    }

    /**
     * The draw alpha for this entity right now: its target opacity, or a point on the
     * ease toward it. First sight seeds at the target so entities entering render
     * distance never fade in from nothing.
     */
    public static float alpha(LivingEntity entity, float partialTick) {
        boolean invisible = entity.isInvisible();
        long now = entity.level().getGameTime();
        State s = STATES.get(entity.getId());
        // Re-resolve the target when the flag flips (gene conditions usually key off it)
        // or when the resolution has aged out (slower condition sources like time of day).
        if (s == null || s.invisible != invisible || now - s.resolvedAt >= TARGET_REFRESH_TICKS) {
            float target = resolveTarget(entity, invisible);
            if (s == null) {
                s = new State(target, target, NEVER, invisible, now);
            } else if (target != s.target) {
                s = new State(eased(s, now, partialTick), target, now, invisible, now);
            } else {
                s = new State(s.from, s.target, s.since, invisible, now);
            }
            STATES.put(entity.getId(), s);
        }
        return eased(s, now, partialTick);
    }

    private static float eased(State s, long now, float partialTick) {
        if (s.since == NEVER) return s.target;
        float t = Math.min(1f, ((now - s.since) + partialTick) / FADE_TICKS);
        return s.from + (s.target - s.from) * t;
    }

    /**
     * The entity's current target opacity: the strongest expressed {@code pheno:opacity}
     * gene whose condition holds on this client, else the invisible flag's hard 0/1.
     * When several hold, the most opaque wins (a shimmer shows through full invisibility).
     */
    private static float resolveTarget(LivingEntity entity, boolean invisible) {
        Float geneAlpha = null;
        for (String geneId : opacityCandidates(entity)) {
            GeneCatalogEntry gene = RootCatalogClient.gene(geneId);
            if (gene == null || !gene.isOpacity()) continue;
            if (!conditionHolds(gene, entity)) continue;
            if (geneAlpha == null || gene.opacityAlpha() > geneAlpha) geneAlpha = gene.opacityAlpha();
        }
        if (geneAlpha != null) return geneAlpha;
        return invisible ? 0f : 1f;
    }

    /** Per-entity synced expressed set first (includes companions), origin-typical grants as fallback. */
    private static Iterable<String> opacityCandidates(LivingEntity entity) {
        Set<String> expressed = RootClientStore.expressedGenes(entity);
        if (RootClientStore.hasExpressionSync(entity) || !expressed.isEmpty()) return expressed;
        String rootId = RootClientStore.resolve(entity);
        if (rootId.isEmpty()) return List.of();
        RootCatalogEntry origin = RootCatalogClient.origin(rootId);
        if (origin == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (RootCatalogEntry.Inherited inherited : origin.inheritedGenes()) ids.add(inherited.geneId());
        return ids;
    }

    private static boolean conditionHolds(GeneCatalogEntry gene, LivingEntity entity) {
        if (gene.conditionJson().isEmpty()) return true;
        Optional<Condition> condition = CONDITIONS.computeIfAbsent(gene.conditionJson(), json -> {
            try {
                return Optional.ofNullable(Conditions.parse(JsonParser.parseString(json)));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        // An unparseable gate never holds: silently widening when the opacity applies is
        // worse than the shimmer missing.
        return condition.isPresent() && condition.get().test(new ConditionContext(entity));
    }
}
