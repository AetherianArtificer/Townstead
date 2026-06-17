package com.aetherianartificer.townstead.pheno.capability;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Folds a flat list of {@link CapabilityContribution}s into a {@link CapabilityView} with
 * deterministic conflict resolution. Per key: inactive contributions are set aside; within each
 * exclusivity group only the highest-priority contribution survives (ties break on the
 * provenance id) while stacking-group members all combine; an active DENY dominates and forces
 * the off/identity value; otherwise the numeric fold runs REPLACE, then ADD, then MULTIPLY,
 * then OR, then MIN, then MAX.
 *
 * <p>This is a best-effort effective value for querying and explaining. While appliers migrate
 * onto the layer, the legacy appliers remain the behavioral source of truth for their effect.
 */
public final class CapabilityResolver {

    private CapabilityResolver() {}

    public static CapabilityView resolve(List<CapabilityContribution> all) {
        Map<CapabilityKey, List<CapabilityContribution>> byKey = new LinkedHashMap<>();
        for (CapabilityContribution c : all) {
            byKey.computeIfAbsent(c.key(), k -> new ArrayList<>()).add(c);
        }
        Map<CapabilityKey, Resolved> out = new LinkedHashMap<>();
        for (Map.Entry<CapabilityKey, List<CapabilityContribution>> e : byKey.entrySet()) {
            out.put(e.getKey(), resolveKey(e.getKey(), e.getValue()));
        }
        return new CapabilityView(out);
    }

    private static Resolved resolveKey(CapabilityKey key, List<CapabilityContribution> list) {
        List<CapabilityContribution> applied = new ArrayList<>();
        List<CapabilityContribution> ignored = new ArrayList<>();

        List<CapabilityContribution> active = new ArrayList<>();
        for (CapabilityContribution c : list) {
            if (c.active()) active.add(c);
            else ignored.add(c);
        }

        // Collapse each exclusivity group to its highest-priority member (mutually exclusive
        // alternatives, only one applies). Stacking-group members are not collapsed: they all
        // combine through the fold below (a per-group stack cap is a future addition).
        Map<String, CapabilityContribution> exclusiveWinner = new LinkedHashMap<>();
        List<CapabilityContribution> candidates = new ArrayList<>();
        for (CapabilityContribution c : active) {
            String group = exclusivityKey(c);
            if (group == null) {
                candidates.add(c);
                continue;
            }
            CapabilityContribution prev = exclusiveWinner.get(group);
            if (prev == null) {
                exclusiveWinner.put(group, c);
            } else if (better(c, prev)) {
                ignored.add(prev);
                exclusiveWinner.put(group, c);
            } else {
                ignored.add(c);
            }
        }
        candidates.addAll(exclusiveWinner.values());

        // An active DENY forces the capability off / to identity.
        boolean denied = false;
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.DENY) { denied = true; break; }
        }
        if (denied) {
            for (CapabilityContribution c : candidates) {
                if (c.op() == Op.DENY) applied.add(c);
                else ignored.add(c);
            }
            double v = key.kind() == ValueKind.FLAG ? 0d : key.identity();
            return new Resolved(key, v, applied, ignored);
        }

        // REPLACE: highest-priority replace sets the base; the rest are ignored.
        CapabilityContribution replaceWinner = null;
        for (CapabilityContribution c : candidates) {
            if (c.op() != Op.REPLACE) continue;
            if (replaceWinner == null) {
                replaceWinner = c;
            } else if (better(c, replaceWinner)) {
                ignored.add(replaceWinner);
                replaceWinner = c;
            } else {
                ignored.add(c);
            }
        }

        double base = key.identity();
        if (replaceWinner != null) {
            base = replaceWinner.value();
            applied.add(replaceWinner);
        }
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.ADD) { base += c.value(); applied.add(c); }
        }
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.MULTIPLY) { base *= c.value(); applied.add(c); }
        }
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.OR) { if (c.value() >= 0.5d) base = Math.max(base, 1d); applied.add(c); }
        }
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.MIN) { base = Math.min(base, c.value()); applied.add(c); }
        }
        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.MAX) { base = Math.max(base, c.value()); applied.add(c); }
        }
        return new Resolved(key, base, applied, ignored);
    }

    /**
     * Folds modifier-style contributions onto a live {@code base} instead of a key identity, in
     * the same order as {@link #resolveKey} (exclusivity collapse, then DENY, then REPLACE, ADD,
     * MULTIPLY, MIN, MAX). All entries are assumed to target one logical value (one or more keys
     * that share a hook), so they combine directly. An active DENY neutralizes the modifiers and
     * returns {@code base} unchanged. This is the apply-to-base variant the base-relative appliers
     * (modifiers, and later damage) query at their vanilla hook.
     */
    public static double applyToBase(double base, List<CapabilityContribution> contributions) {
        List<CapabilityContribution> active = new ArrayList<>();
        for (CapabilityContribution c : contributions) {
            if (c.active()) active.add(c);
        }

        Map<String, CapabilityContribution> exclusiveWinner = new LinkedHashMap<>();
        List<CapabilityContribution> candidates = new ArrayList<>();
        for (CapabilityContribution c : active) {
            String group = exclusivityKey(c);
            if (group == null) {
                candidates.add(c);
                continue;
            }
            CapabilityContribution prev = exclusiveWinner.get(group);
            if (prev == null || better(c, prev)) exclusiveWinner.put(group, c);
        }
        candidates.addAll(exclusiveWinner.values());

        for (CapabilityContribution c : candidates) {
            if (c.op() == Op.DENY) return base;
        }

        double v = base;
        CapabilityContribution replaceWinner = null;
        for (CapabilityContribution c : candidates) {
            if (c.op() != Op.REPLACE) continue;
            if (replaceWinner == null || better(c, replaceWinner)) replaceWinner = c;
        }
        if (replaceWinner != null) v = replaceWinner.value();
        for (CapabilityContribution c : candidates) if (c.op() == Op.ADD) v += c.value();
        for (CapabilityContribution c : candidates) if (c.op() == Op.MULTIPLY) v *= c.value();
        for (CapabilityContribution c : candidates) if (c.op() == Op.MIN) v = Math.min(v, c.value());
        for (CapabilityContribution c : candidates) if (c.op() == Op.MAX) v = Math.max(v, c.value());
        return v;
    }

    @Nullable
    private static String exclusivityKey(CapabilityContribution c) {
        return c.exclusivityGroup() != null ? "x:" + c.exclusivityGroup() : null;
    }

    private static boolean better(CapabilityContribution a, CapabilityContribution b) {
        if (a.priority() != b.priority()) return a.priority() > b.priority();
        return a.provenance().source().toString().compareTo(b.provenance().source().toString()) < 0;
    }
}
