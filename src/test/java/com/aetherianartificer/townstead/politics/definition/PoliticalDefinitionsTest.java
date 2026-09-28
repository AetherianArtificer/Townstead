package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.aetherianartificer.townstead.social.BondKind;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.id;
import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalDefinitionsTest {
    @BeforeAll
    static void registerPhenoVocabulary() {
        PoliticsFixtures.registerPheno();
    }

    @AfterEach
    void clearDefinitions() {
        PoliticsFixtures.clear();
    }

    @Test
    void bundledBondsAndFactionKindsFormOneCoherentSet() throws Exception {
        Map<ResourceLocation, BondKind> bonds = PoliticsFixtures.bonds();
        Map<ResourceLocation, FactionKind> kinds = PoliticsFixtures.kinds();

        assertEquals(Set.of(id("townstead:friendship"), id("townstead:marriage"), id("townstead:citizenship"),
                id("townstead:faction_leader"), id("townstead:presiding_councilor"), id("townstead:councilor"),
                id("townstead:accord")), bonds.keySet());
        assertEquals(Set.of(id("townstead:village_council"), id("townstead:player_faction"), id("townstead:free_settlement")), kinds.keySet());
        for (FactionKind kind : kinds.values()) {
            assertTrue(PoliticalDefinitions.validate(kind, bonds).isEmpty(),
                    () -> kind.id() + ": " + PoliticalDefinitions.validate(kind, bonds));
        }

        FactionKind playerFaction = kinds.get(id("townstead:player_faction"));
        assertEquals(FactionKind.APPLICATION, playerFaction.membership().admission());
        assertEquals(id("townstead:citizenship"), playerFaction.membership().bond());
        FactionKind.Office leader = playerFaction.office(id("townstead:faction_leader"));
        assertTrue(leader.founder());
        assertEquals(1, leader.minimum());
        assertEquals(1, leader.maximum());
        assertEquals(id("townstead:faction_leader"), playerFaction.governance().head());
        assertEquals(GovernanceRoutes.FAVOR, playerFaction.governance().succession());

        FactionKind councilKind = kinds.get(id("townstead:village_council"));
        assertTrue(councilKind.generated());
        assertEquals(FactionKind.RESIDENCE, councilKind.membership().admission());
        GovernanceDefinition council = councilKind.governance();
        assertEquals(id("townstead:presiding_councilor"), council.head());
        assertEquals(GovernanceRoutes.COUNCIL_VOTE, council.succession());
        assertEquals(50, council.legitimacy().base());
        assertEquals(2, council.legitimacy().sources().size());
        assertEquals(2, council.routes().size());

        FactionKind free = kinds.get(id("townstead:free_settlement"));
        assertTrue(free.offices().isEmpty());
        assertNull(free.governance());

        BondKind leaderBond = bonds.get(id("townstead:faction_leader"));
        assertTrue(leaderBond.officeShaped());
        assertTrue(leaderBond.roleFor(BondKind.Party.FACTION).gives().contains(id("townstead:govern_faction")));
        assertEquals(id("townstead:citizenship"), leaderBond.roleFor(BondKind.Party.PERSON).requires());

        PoliticalDefinitions.replace(kinds);
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("townstead:default_village"),
                PoliticsFixtures.resource("data/townstead/founding_profile/default_village.json"), Map.of());
        assertTrue(FoundingProfiles.validate(profile).isEmpty(), () -> String.join("\n", FoundingProfiles.validate(profile)));
        assertEquals(id("townstead:village_council"), profile.faction().kind());
        assertEquals(3, profile.faction().bundles().size());
        assertEquals(Set.of(id("townstead:presiding_councilor"), id("townstead:councilor")), profile.faction().bundles().get(0));
        assertEquals(id("townstead:cultural_affinity"), profile.population().strategy());
        assertTrue(profile.population().adjustments().isEmpty(), "the starter population is an open strategy, not an allowlist");
    }

    @Test
    void anOlderGovernmentBlockStillLoads() {
        PoliticalDefinitions.replace(PoliticsFixtures.kinds());
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("test:tusk_horde"), json("""
                {
                  "schema":"townstead:founding_profile/v1",
                  "government":{
                    "organization_kind":"townstead:village_council",
                    "name_pattern":"{village} Council",
                    "seats":[
                      {"roles":["townstead:presiding_councilor","townstead:councilor"],"count":1},
                      {"roles":["townstead:councilor"],"count":2}
                    ]
                  }
                }
                """), Map.of());

        assertTrue(profile.faction().legacy());
        assertEquals(id("townstead:village_council"), profile.faction().kind());
        assertEquals(3, profile.faction().bundles().size());
        assertTrue(FoundingProfiles.validate(profile).isEmpty());
    }

    @Test
    void aProfileWithoutAFactionIsValid() {
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("townstead:no_formal_government"), json("""
                {"schema":"townstead:founding_profile/v1","name":"No Formal Government","weight":0}
                """), Map.of());

        assertNull(profile.faction());
        assertTrue(FoundingProfiles.validate(profile).isEmpty());
    }

    @Test
    void aSeatNamingAnOfficeTheKindLacksIsReported() {
        PoliticalDefinitions.replace(PoliticsFixtures.kinds());
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("test:empire"), json("""
                {"schema":"townstead:founding_profile/v1",
                 "faction":{"kind":"townstead:village_council","seats":[{"offices":["test:emperor"]}]}}
                """), Map.of());

        List<String> errors = FoundingProfiles.validate(profile);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("test:emperor"));
    }

    @Test
    void unknownPhenoEligibilityFailsClosed() {
        JsonObject json = kind(null);
        json.getAsJsonObject("membership").add("eligibility", JsonParser.parseString("{\"type\":\"example:not_registered\"}"));

        assertThrows(IllegalArgumentException.class, () -> FactionKind.parse(id("test:closed"), json, Map.of()));
    }

    @Test
    void anUnknownAdmissionIsRejected() {
        JsonObject json = kind(null);
        json.getAsJsonObject("membership").addProperty("admission", "test:by_lottery");

        assertThrows(IllegalArgumentException.class, () -> FactionKind.parse(id("test:lottery"), json, Map.of()));
    }

    @Test
    void governanceNamesOnlyImplementedRoutes() {
        assertThrows(IllegalArgumentException.class, () -> FactionKind.parse(id("test:lottery"),
                kind("{\"head\":\"test:warden\",\"succession\":\"test:trial_by_lottery\"}"), Map.of()));
    }

    @Test
    void governanceOfficesMustBeOfficesOfTheKind() {
        assertThrows(IllegalArgumentException.class, () -> FactionKind.parse(id("test:empire"),
                kind("{\"head\":\"test:emperor\",\"succession\":\"townstead:favor\"}"), Map.of()));
    }

    @Test
    void malformedOfficeCardinalityIsRejected() {
        JsonObject json = kind(null);
        json.getAsJsonArray("offices").get(0).getAsJsonObject().addProperty("min", 2);

        assertThrows(IllegalArgumentException.class, () -> FactionKind.parse(id("test:broken"), json, Map.of()));
    }

    @Test
    void aKindWhoseOfficeIsNotAnOfficeShapedBondIsReported() {
        FactionKind kind = FactionKind.parse(id("test:wardens"), kind(null), Map.of());
        Map<ResourceLocation, BondKind> bonds = Map.of(id("townstead:citizenship"), PoliticsFixtures.bonds().get(id("townstead:citizenship")),
                id("test:warden"), BondKind.personal(id("test:warden"), "Warden", 0, true, true, null));

        List<String> errors = PoliticalDefinitions.validate(kind, bonds);
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("test:warden"));
    }

    @Test
    void aRoleThatGivesLandMustBeASingleParent() {
        assertThrows(IllegalArgumentException.class, () -> BondKind.parse(id("test:vassalage"), json("""
                {"schema":"townstead:bond/v2","roles":{
                  "liege":{"party":"faction"},
                  "vassal":{"party":"faction","gives":["townstead:land"]}}}
                """), Map.of()));
    }

    @Test
    void versionOneBondKindsLoadAsTiesBetweenPeople() {
        BondKind marriage = BondKind.parse(id("test:marriage"), json("""
                {"schema":"townstead:bond_kind/v1","max_active":1,"unique_per_pair":true,"symmetric":true,"source":"mca:marriage"}
                """), Map.of());

        assertTrue(marriage.personal());
        assertTrue(marriage.symmetric());
        assertEquals(1, marriage.maxActive());
        assertTrue(marriage.uniquePerPair());
        assertEquals("mca:marriage", marriage.source());
        assertFalse(marriage.officeShaped());
    }

    /** A faction kind with one office, {@code test:warden}. */
    private static JsonObject kind(String governance) {
        JsonObject json = json("""
                {
                  "schema":"townstead:faction/v1",
                  "membership":{"bond":"townstead:citizenship","admission":"townstead:open"},
                  "offices":[{"bond":"test:warden","min":1,"max":1}]
                }
                """);
        if (governance != null) json.add("governance", JsonParser.parseString(governance));
        return json;
    }
}
