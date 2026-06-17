package com.aetherianartificer.townstead.pheno.capability;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityResolverTest {

    private static final CapabilityKey SPEED = CapabilityKey.scalar(ResourceLocation.tryParse("test:speed"));

    private static Provenance prov(String id) {
        return new Provenance(ResourceLocation.tryParse(id), SourceKind.GENE, null);
    }

    private static CapabilityContribution mul(double v, int prio, String stack, String excl, String src, boolean active) {
        return new CapabilityContribution(SPEED, Op.MULTIPLY, v, prio, stack, excl, prov(src), active);
    }

    @Test
    void multiplyFoldCombinesAll() {
        CapabilityView view = CapabilityResolver.resolve(List.of(
                mul(1.5, 0, null, null, "test:a", true),
                mul(2.0, 0, null, null, "test:b", true)));
        assertEquals(3.0, view.numeric(SPEED, 1.0), 1e-9);
    }

    @Test
    void exclusivityKeepsHighestPriorityOnly() {
        CapabilityView view = CapabilityResolver.resolve(List.of(
                mul(2.0, 1, null, "grp", "test:hi", true),
                mul(5.0, 0, null, "grp", "test:lo", true)));
        assertEquals(2.0, view.numeric(SPEED, 1.0), 1e-9);
    }

    @Test
    void stackingGroupMembersCombine() {
        CapabilityView view = CapabilityResolver.resolve(List.of(
                mul(2.0, 0, "s", null, "test:a", true),
                mul(2.0, 0, "s", null, "test:b", true)));
        assertEquals(4.0, view.numeric(SPEED, 1.0), 1e-9);
    }

    @Test
    void denyDominatesAFlag() {
        CapabilityKey fly = CapabilityKey.flag(ResourceLocation.tryParse("test:fly"));
        CapabilityView view = CapabilityResolver.resolve(List.of(
                CapabilityContribution.flag(fly, prov("test:grant"), true),
                new CapabilityContribution(fly, Op.DENY, 0, 0, null, null, prov("test:deny"), true)));
        assertFalse(view.flag(fly));
    }

    @Test
    void flagOnWhenAnyGrants() {
        CapabilityKey fly = CapabilityKey.flag(ResourceLocation.tryParse("test:fly"));
        CapabilityView view = CapabilityResolver.resolve(List.of(
                CapabilityContribution.flag(fly, prov("test:grant"), true)));
        assertTrue(view.flag(fly));
    }

    @Test
    void inactiveContributionsAreIgnored() {
        CapabilityView view = CapabilityResolver.resolve(List.of(
                mul(9.0, 0, null, null, "test:a", false)));
        assertEquals(1.0, view.numeric(SPEED, 1.0), 1e-9);
    }
}
