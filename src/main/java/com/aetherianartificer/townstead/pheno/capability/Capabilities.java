package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and front door for the capability layer. Mirrors {@code Powers}: any number of
 * {@link CapabilitySource}s register here, and callers resolve an entity's effective
 * capabilities ({@link #resolve}) or read the raw contributions ({@link #collect}, used by
 * {@code /pheno explain}). Source-agnostic: Townstead systems query the {@link CapabilityView},
 * never a particular source.
 */
public final class Capabilities {

    private static final List<CapabilitySource> SOURCES = new CopyOnWriteArrayList<>();

    private Capabilities() {}

    public static void register(CapabilitySource source) {
        SOURCES.add(source);
    }

    public static List<CapabilityContribution> collect(LivingEntity entity) {
        return collect(entity, null);
    }

    /** Contributions for a mechanic acting on {@code subject}; item-scoped sources test it. */
    public static List<CapabilityContribution> collect(LivingEntity entity,
                                                       @org.jetbrains.annotations.Nullable net.minecraft.world.item.ItemStack subject) {
        CapabilityCollector collector = new CapabilityCollector(subject);
        for (CapabilitySource source : SOURCES) {
            source.contribute(entity, collector);
        }
        return new ArrayList<>(collector.contributions());
    }

    public static CapabilityView resolve(LivingEntity entity) {
        return CapabilityResolver.resolve(collect(entity));
    }

    /**
     * Applies every contribution whose key is in {@code keys} onto a live {@code base} (the
     * apply-to-base fold). Base-relative appliers (modifiers) call this at their vanilla hook so
     * genetics and professions stack with op/priority/provenance. Passing several keys folds them
     * onto the same base (used by per-discriminator targets that combine a specific key with the
     * undiscriminated one). Resolution is per-event, not per-tick, so the collect cost is fine.
     */
    public static double applyToBase(LivingEntity entity, double base, CapabilityKey... keys) {
        return applyToBase(entity, null, base, keys);
    }

    /** {@link #applyToBase} for a mechanic acting on {@code subject} (see {@link CapabilityCollector#subjectItem}). */
    public static double applyToBase(LivingEntity entity,
                                     @org.jetbrains.annotations.Nullable net.minecraft.world.item.ItemStack subject,
                                     double base, CapabilityKey... keys) {
        List<CapabilityContribution> all = collect(entity, subject);
        if (all.isEmpty()) return base;
        Set<CapabilityKey> want = new HashSet<>(Arrays.asList(keys));
        List<CapabilityContribution> matching = new ArrayList<>();
        for (CapabilityContribution c : all) {
            if (want.contains(c.key())) matching.add(c);
        }
        if (matching.isEmpty()) return base;
        return CapabilityResolver.applyToBase(base, matching);
    }
}
