package com.aetherianartificer.townstead.culture;

import com.aetherianartificer.townstead.spirit.SpiritTotals;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubculturesTest {
    private static final ResourceLocation ROOT = id("test:rosaguarda");
    private static final ResourceLocation MERCHANT = id("test:mercantile");
    private static final ResourceLocation COURTLY = id("test:courtly");
    private static final ResourceLocation REPUBLIC = id("test:merchant_republic");
    private static final ResourceLocation TABLE = id("test:knights_table");
    private static final ResourceLocation NAMES = id("test:faction_names");
    private static final ResourceLocation LIVERY = id("test:parade_plate");

    @AfterEach
    void clear() {
        Cultures.replace(Map.of());
    }

    @Test
    void subcultureInheritsWhatItLeavesUnsetAndShowsTheRootName() {
        Map<ResourceLocation, Culture> resolved = CultureJsonLoader.inherit(loaded());
        Culture merchant = resolved.get(MERCHANT);
        assertEquals("Rosaguarda", merchant.displayName().getString());
        assertEquals(NAMES, merchant.factionNames());
        assertEquals(LIVERY, merchant.clothing().livery());
        assertEquals(ROOT, merchant.parent());
        assertEquals(Map.of("commercial", 1.0F), merchant.spirit());
    }

    @Test
    void missingParentLeavesTheCultureStandingAlone() {
        Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
        loaded.put(MERCHANT, sub(MERCHANT, id("test:absent"), Map.of(), List.of()));
        Culture alone = CultureJsonLoader.inherit(loaded).get(MERCHANT);
        assertNull(alone.parent());
        assertFalse(alone.isSubculture());
    }

    @Test
    void referencesToTheParentReachItsSubcultures() {
        Cultures.replace(CultureJsonLoader.inherit(loaded()));
        assertTrue(Cultures.within(MERCHANT, ROOT));
        assertFalse(Cultures.within(ROOT, MERCHANT));
        assertTrue(Cultures.matches(MERCHANT.toString(), Set.of(ROOT)));
        assertFalse(Cultures.matches(MERCHANT.toString(), Set.of(COURTLY)));
        assertEquals(ROOT, Cultures.rootOf(MERCHANT));
        assertEquals(Set.of(ROOT), Cultures.rootIds());
        assertEquals(2, Cultures.subculturesOf(ROOT).size());
    }

    @Test
    void foundingFollowsWhatTheVillageBuilt() {
        Cultures.replace(CultureJsonLoader.inherit(loaded()));
        SpiritTotals harbor = new SpiritTotals(Map.of("commercial", 30, "martial", 0), 30, 3);
        Culture merchant = Cultures.get(MERCHANT);
        assertEquals(1.0, Subcultures.spiritFit(merchant, harbor), 1e-6);
        assertTrue(Subcultures.foundingFactor(ROOT, REPUBLIC, harbor) > Subcultures.foundingFactor(ROOT, TABLE, harbor));
        assertEquals(MERCHANT, Subcultures.forFounding(ROOT, REPUBLIC, harbor, RandomSource.create(1)));
        assertEquals(ROOT, Subcultures.forFounding(ROOT, id("test:nobody_founds_this"), harbor, RandomSource.create(1)));
    }

    @Test
    void anOrdinaryVillagePullsNobodyAndOnlyTheExcessCounts() {
        Cultures.replace(CultureJsonLoader.inherit(loaded()));
        com.aetherianartificer.townstead.spirit.SpiritBaseline.replace(Map.of("commercial", 0.25F));
        try {
            Culture merchant = Cultures.get(MERCHANT);
            SpiritTotals ordinary = new SpiritTotals(Map.of("commercial", 25, "pastoral", 75), 100, 5);
            SpiritTotals port = new SpiritTotals(Map.of("commercial", 75, "pastoral", 25), 100, 5);
            assertEquals(0.0, Subcultures.spiritFit(merchant, ordinary), 1e-6);
            assertEquals(0.5, Subcultures.spiritFit(merchant, port), 1e-6);
            assertEquals(1.0F, Subcultures.foundingFactor(ROOT, REPUBLIC, ordinary), 1e-6);
        } finally {
            com.aetherianartificer.townstead.spirit.SpiritBaseline.replace(Map.of());
        }
    }

    @Test
    void blendedClothingWeighsEachRateByShare() {
        CultureClothing loud = new CultureClothing(List.of(new CultureClothing.SkinBias("test:ribbons/", 9F, Set.of())),
                List.of(), List.of(), List.of(), null);
        Culture a = new Culture(id("test:a"), Component.literal("A"), null, null, loud);
        Culture b = new Culture(id("test:b"), Component.literal("B"), null, null, CultureClothing.NONE);
        Map<Culture, Float> blend = new LinkedHashMap<>();
        blend.put(a, 0.5F);
        blend.put(b, 0.5F);
        CultureClothing mixed = CultureBlends.mix(blend);
        assertEquals(3.0, mixed.rateForSkin("test:ribbons/red", null), 1e-4);
    }

    private static Map<ResourceLocation, Culture> loaded() {
        Map<ResourceLocation, Culture> out = new LinkedHashMap<>();
        CultureClothing clothing = new CultureClothing(List.of(), List.of(), List.of(), List.of(), LIVERY);
        out.put(ROOT, new Culture(ROOT, Component.literal("Rosaguarda"), null, null, clothing, NAMES));
        out.put(MERCHANT, sub(MERCHANT, ROOT, Map.of("commercial", 1.0F), List.of(new Culture.Form(REPUBLIC, 3))));
        out.put(COURTLY, sub(COURTLY, ROOT, Map.of("martial", 1.0F), List.of(new Culture.Form(TABLE, 3))));
        return out;
    }

    private static Culture sub(ResourceLocation id, ResourceLocation parent, Map<String, Float> spirit, List<Culture.Form> forms) {
        return new Culture(id, Component.literal(id.getPath()), null, null, CultureClothing.NONE, null, null, parent, spirit, forms);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
