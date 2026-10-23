package com.aetherianartificer.townstead.root;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lineage shares within one ancestry: blending, profile matching, fallback naming, old-save healing. */
class HeritageLineageTest {

    private static final ResourceLocation DRACONIC = id("draconic");
    private static final ResourceLocation HUMAN = id("human");
    private static final ResourceLocation FIRE = id("fire_drake");
    private static final ResourceLocation ICE = id("ice_drake");
    private static final ResourceLocation VOLT = id("volt_drake");

    private static final HeritageProfile STEAM = new HeritageProfile(id("steam_drake"), Component.literal("Steam Drake"),
            null, null, 20, Map.of(), Map.of(FIRE, new GeneRange(0.3f, 0.7f), ICE, new GeneRange(0.3f, 0.7f)));

    @AfterEach
    void reset() {
        HeritageRegistry.replaceAll(Map.of());
        LineageRegistry.replaceAll(Map.of());
    }

    @Test
    void sameAncestryChildBlendsLineagesNotAncestry() {
        Heritage child = Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, ICE));

        assertEquals(1f, child.fractionOf(DRACONIC), 1e-4);
        assertEquals(0.5f, child.lineageFractionOf(FIRE), 1e-4);
        assertEquals(0.5f, child.lineageFractionOf(ICE), 1e-4);
    }

    @Test
    void lineageProfileNamesTheCrossButNotThePurebreds() {
        assertTrue(STEAM.matches(Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, ICE))));
        assertFalse(STEAM.matches(Heritage.pure(DRACONIC, FIRE)));
        // A quarter Volt is more than the slack a Fire x Ice profile allows.
        Heritage steam = Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, ICE));
        assertFalse(STEAM.matches(Heritage.blend(steam, Heritage.pure(DRACONIC, VOLT))));
    }

    @Test
    void ancestryProfilesStillMatchLineagedHeritage() {
        HeritageProfile drakeblood = new HeritageProfile(id("drakeblood"), Component.literal("Drakeblood"), null, null,
                10, Map.of(HUMAN, new GeneRange(0.3f, 0.7f), DRACONIC, new GeneRange(0.3f, 0.7f)));

        assertTrue(drakeblood.matches(Heritage.blend(Heritage.pure(HUMAN), Heritage.pure(DRACONIC, FIRE))));
    }

    @Test
    void openLineagesLetAnyPartnerLineageMakeUpTheRest() {
        ResourceLocation primian = id("primian");
        java.util.function.Function<Boolean, HeritageProfile> halfDrake = open -> new HeritageProfile(id("half_fire"),
                Component.literal("Half-Fire"), null, null, 10,
                Map.of(HUMAN, new GeneRange(0.3f, 0.7f), DRACONIC, new GeneRange(0.3f, 0.7f)),
                Map.of(FIRE, new GeneRange(0.3f, 0.7f)), java.util.List.of(), open);
        Heritage withPrimian = Heritage.blend(Heritage.pure(HUMAN, primian), Heritage.pure(DRACONIC, FIRE));

        assertFalse(halfDrake.apply(false).matches(withPrimian));
        assertTrue(halfDrake.apply(true).matches(withPrimian));
        // the ancestry bands still hold: an elf partner is not a human one
        assertFalse(halfDrake.apply(true).matches(Heritage.blend(Heritage.pure(id("elf"), primian), Heritage.pure(DRACONIC, FIRE))));
    }

    @Test
    void unnamedLineageMixGetsAGeneratedName() {
        HeritageRegistry.replaceAll(Map.of(STEAM.id(), STEAM));
        Heritage fireVolt = Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, VOLT));
        Heritage fireIce = Heritage.blend(Heritage.pure(DRACONIC, FIRE), Heritage.pure(DRACONIC, ICE));
        Component founder = Component.literal("Fire Drake");

        assertEquals("Steam Drake", HeritageRegistry.resolve(fireIce, founder).displayName().getString());
        assertEquals("fire_drake-volt_drake", HeritageRegistry.resolve(fireVolt, founder).displayName().getString());
        assertEquals("Fire Drake", HeritageRegistry.resolve(Heritage.pure(DRACONIC, FIRE), founder).displayName().getString());
    }

    @Test
    void oldSavePurebredHealsItsLineageButAMixDoesNot() {
        Heritage seed = Heritage.pure(DRACONIC, FIRE);

        assertEquals(1f, Heritage.pure(DRACONIC).withLineagesFrom(seed).lineageFractionOf(FIRE), 1e-4);
        Heritage mixed = Heritage.blend(Heritage.pure(HUMAN), Heritage.pure(DRACONIC));
        assertSame(mixed, mixed.withLineagesFrom(seed));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("test:" + path);
    }
}
