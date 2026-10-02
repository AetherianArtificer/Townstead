package com.aetherianartificer.townstead.clothing.dress;

import com.aetherianartificer.townstead.clothing.ClothingDefs;
import com.aetherianartificer.townstead.clothing.ClothingEntry;
import com.aetherianartificer.townstead.clothing.ClothingLayer;
import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.clothing.WornPiece;
import com.aetherianartificer.townstead.clothing.policy.WardrobePolicy;
import com.aetherianartificer.townstead.clothing.policy.WardrobeResolver;
import com.aetherianartificer.townstead.culture.CultureClothing;
import com.aetherianartificer.townstead.temperature.TemperatureData;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DressDecisionTest {

    static ResourceLocation id(String s) {
        return com.aetherianartificer.townstead.data.DataPackLang.parseId(s);
    }

    static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    static ClothingEntry entry(String body) {
        ClothingEntry parsed = ClothingEntry.parse(id("t:d"), 0, json(body));
        assertNotNull(parsed);
        return parsed;
    }

    /** Stacks need registries, so tests wear records: the decision reads the entry, the task reads the stack. */
    static WornPiece worn(ClothingEntry entry, String source) {
        return WornPiece.ofRecord(entry.layer(), entry.slot(), entry, source);
    }

    static WardrobePolicy policy(String layers) {
        WardrobePolicy parsed = WardrobePolicy.parse(id("t:p"), json("{ \"layers\": " + layers + " }"));
        assertNotNull(parsed);
        return parsed;
    }

    static WardrobeResolver.Plan plan(WardrobePolicy policy) {
        return new WardrobeResolver.Plan(policy.layers(),
                Map.of(ClothingLayer.OUTERWEAR, policy, ClothingLayer.ACCESSORY, policy, ClothingLayer.BASE, policy));
    }

    static final ClothingEntry SWEATER = entry(
            "{ \"item\": \"wp:sweater\", \"layer\": \"outerwear\", \"slot\": \"body\", \"thermal\": { \"offset\": 0.5 } }");
    static final ClothingEntry STRAW_HAT = entry(
            "{ \"item\": \"acc:straw_hat\", \"layer\": \"accessory\", \"slot\": \"head\", \"thermal\": { \"heat_resistance\": 0.5 } }");

    @AfterEach
    void reset() {
        ClothingDefs.replaceAll(List.of(), List.of());
    }

    @Test
    void unmetRequiredLayerBecomesAFetch() {
        WardrobeResolver.Plan winter = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"required\", \"select\": { \"thermal\": \"warm\" } } }"));
        List<DressDecision.Action> actions = DressDecision.decide(winter, List.of(), false, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(1, actions.size());
        assertEquals(DressDecision.Kind.FETCH, actions.get(0).kind());
        assertEquals(ClothingLayer.OUTERWEAR, actions.get(0).layer());
        assertTrue(actions.get(0).required());
    }

    @Test
    void metLayerAsksForNothing() {
        WardrobeResolver.Plan winter = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"required\", \"select\": { \"thermal\": \"warm\" } } }"));
        List<DressDecision.Action> actions = DressDecision.decide(winter, List.of(worn(SWEATER, "townstead:curios_slots")),
                false, null, false, CultureClothing.NONE, List.of());
        assertTrue(actions.isEmpty());
    }

    @Test
    void noneRuleStowsWhatIsWornAndArmourManagedPiecesAreLeftAlone() {
        WardrobeResolver.Plan summer = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"none\" } }"));
        WornPiece curio = worn(SWEATER, "townstead:curios_slots");
        WornPiece armorSlot = worn(SWEATER, DressDecision.ARMOR_SOURCE);

        List<DressDecision.Action> free = DressDecision.decide(summer, List.of(curio, armorSlot), false, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(2, free.size());
        assertTrue(free.stream().allMatch(a -> a.kind() == DressDecision.Kind.STOW));

        List<DressDecision.Action> managed = DressDecision.decide(summer, List.of(curio, armorSlot), true, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(1, managed.size());
        assertEquals(curio, managed.get(0).piece());
    }

    @Test
    void noneRuleWithASelectorStowsOnlyWhatItAdmits() {
        WardrobeResolver.Plan mild = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"none\", \"select\": { \"thermal\": \"warm\" } } }"));
        ClothingEntry linenShirt = entry(
                "{ \"item\": \"wp:linen_shirt\", \"layer\": \"outerwear\", \"slot\": \"body\" }");
        WornPiece sweater = worn(SWEATER, "townstead:curios_slots");
        WornPiece shirt = worn(linenShirt, "townstead:curios_slots");

        List<DressDecision.Action> actions = DressDecision.decide(mild, List.of(sweater, shirt), false, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(1, actions.size());
        assertEquals(DressDecision.Kind.STOW, actions.get(0).kind());
        assertEquals(sweater, actions.get(0).piece());
    }

    @Test
    void aCarriedPieceMeetsARuleAndIsStowedByNone() {
        WornPiece pocketed = worn(SWEATER, "townstead:carried");
        WardrobeResolver.Plan cold = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"required\", \"select\": { \"thermal\": \"warm\" } } }"));
        assertTrue(DressDecision.decide(cold, List.of(pocketed), false, null, false, CultureClothing.NONE, List.of())
                .isEmpty());

        WardrobeResolver.Plan hot = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"outerwear\": { \"require\": \"none\", \"select\": { \"thermal\": \"warm\" } } }"));
        List<DressDecision.Action> actions = DressDecision.decide(hot, List.of(pocketed), false, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(1, actions.size());
        assertEquals(DressDecision.Kind.STOW, actions.get(0).kind());
        assertEquals(pocketed, actions.get(0).piece());
    }

    @Test
    void preferredLayerActsOnlyOffShiftAndSortsAfterRequired() {
        WardrobeResolver.Plan plan = plan(policy(
                "{ \"base\": { \"skin\": \"x:y\" }, \"accessory\": { \"require\": \"preferred\", \"select\": { \"slot\": \"head\" } },"
                + " \"outerwear\": { \"require\": \"required\", \"select\": { \"thermal\": \"warm\" } } }"));
        List<DressDecision.Action> offShift = DressDecision.decide(plan, List.of(), false, null, false,
                CultureClothing.NONE, List.of());
        assertEquals(2, offShift.size());
        assertTrue(offShift.get(0).required());
        assertFalse(offShift.get(1).required());

        List<DressDecision.Action> onShift = DressDecision.decide(plan, List.of(), false, null, true,
                CultureClothing.NONE, List.of());
        assertEquals(1, onShift.size());
        assertEquals(ClothingLayer.OUTERWEAR, onShift.get(0).layer());
    }

    @Test
    void withoutAPlanTheTiersDecide() {
        List<DressDecision.Action> cold = DressDecision.decide(WardrobeResolver.Plan.EMPTY, List.of(), false,
                TemperatureData.Tier.COLD, false, CultureClothing.NONE, List.of());
        assertEquals(1, cold.size());
        assertEquals(DressDecision.Kind.FETCH, cold.get(0).kind());
        assertTrue(cold.get(0).selector().query().test(SWEATER));
        assertFalse(cold.get(0).selector().query().test(STRAW_HAT));

        List<DressDecision.Action> dressedCold = DressDecision.decide(WardrobeResolver.Plan.EMPTY,
                List.of(worn(SWEATER, "townstead:curios_slots")), false, TemperatureData.Tier.COLD, false,
                CultureClothing.NONE, List.of());
        assertTrue(dressedCold.isEmpty());

        List<DressDecision.Action> hot = DressDecision.decide(WardrobeResolver.Plan.EMPTY,
                List.of(worn(SWEATER, "townstead:curios_slots")), false, TemperatureData.Tier.HOT, false,
                CultureClothing.NONE, List.of());
        assertEquals(1, hot.size());
        assertEquals(DressDecision.Kind.STOW, hot.get(0).kind());

        List<DressDecision.Action> comfortable = DressDecision.decide(WardrobeResolver.Plan.EMPTY, List.of(), false,
                TemperatureData.Tier.COMFORTABLE, false, CultureClothing.NONE, List.of());
        assertTrue(comfortable.isEmpty());
    }

    @Test
    void selectorsAdmitStacksBySetCultureBodyAndQuery() {
        var set = com.aetherianartificer.townstead.clothing.ClothingSet.parse(id("t:winter"), json(
                "{ \"members\": [ { \"item\": \"wp:sweater\", \"layer\": \"outerwear\" } ] }"), Map.of());
        assertNotNull(set);
        ClothingDefs.replaceAll(List.of(), List.of(set));
        ClothingEntry member = ClothingDefs.members(id("t:winter")).get(0);

        assertTrue(ClothingSelectors.admits(new WardrobePolicy.Selector(id("t:winter"), false, false, null, null),
                member, CultureClothing.NONE, List.of()));
        assertFalse(ClothingSelectors.admits(new WardrobePolicy.Selector(id("t:winter"), false, false, null, null),
                STRAW_HAT, CultureClothing.NONE, List.of()));

        CultureClothing culture = CultureClothing.parse(json("{ \"clothing\": { \"sets\": [\"t:winter\"] } }"));
        assertTrue(ClothingSelectors.admits(new WardrobePolicy.Selector(null, true, false, null, null),
                member, culture, List.of()));
        assertTrue(ClothingSelectors.admits(new WardrobePolicy.Selector(null, false, true, null, null),
                STRAW_HAT, CultureClothing.NONE, List.of(STRAW_HAT)));
        assertFalse(ClothingSelectors.admits(new WardrobePolicy.Selector(null, false, false, "x:y", null),
                STRAW_HAT, CultureClothing.NONE, List.of()));
        assertTrue(ClothingSelectors.admits(new WardrobePolicy.Selector(null, false, false, null,
                ClothingQuery.parse(json("{ \"thermal\": \"cool\" }"))), STRAW_HAT, CultureClothing.NONE, List.of()));
        assertTrue(ClothingSelectors.admits(WardrobePolicy.Selector.ANYTHING, null, CultureClothing.NONE, List.of()));
    }
}
