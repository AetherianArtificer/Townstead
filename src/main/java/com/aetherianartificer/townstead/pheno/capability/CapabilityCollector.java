package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Sink a {@link CapabilitySource} writes its contributions into. A thin wrapper over a list so
 * the source API stays additive and sources stay unaware of resolution order. A resolution made
 * for one mechanic may carry that mechanic's {@link #subjectItem() subject item} (the tool taking
 * wear, the item on the anvil) so item-scoped contributions can decide whether they apply.
 */
public final class CapabilityCollector {

    private final List<CapabilityContribution> contributions = new ArrayList<>();
    private final @Nullable ItemStack subjectItem;

    public CapabilityCollector() {
        this(null);
    }

    public CapabilityCollector(@Nullable ItemStack subjectItem) {
        this.subjectItem = subjectItem;
    }

    /** The item the mechanic being resolved acts on, or {@code null} when it acts on no item. */
    public @Nullable ItemStack subjectItem() {
        return subjectItem;
    }

    public void add(CapabilityContribution contribution) {
        contributions.add(contribution);
    }

    public void flag(CapabilityKey key, Provenance provenance, boolean active) {
        contributions.add(CapabilityContribution.flag(key, provenance, active));
    }

    public void numeric(CapabilityKey key, Op op, double value, Provenance provenance, boolean active) {
        contributions.add(CapabilityContribution.numeric(key, op, value, provenance, active));
    }

    public List<CapabilityContribution> contributions() {
        return contributions;
    }
}
