package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.LegacyPolitics;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.id;
import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalStateTest {
    private static final ResourceLocation CLAIM = id("warstead:administer_claim");
    private static final ResourceLocation WARDEN = id("test:warden");
    private static final ResourceLocation VASSALAGE = id("test:vassalage");
    private static final ResourceLocation CITIZENSHIP = FactionBonds.CITIZENSHIP;
    private static final ResourceLocation A = id("test:river_wardens");
    private static final ResourceLocation B = id("test:merchant_guild");
    private static final ResourceLocation C = id("test:high_crown");

    @BeforeEach
    void definitions() {
        PoliticsFixtures.load();
        Map<ResourceLocation, BondKind> bonds = new HashMap<>(BondKinds.all());
        bonds.put(WARDEN, BondKind.parse(WARDEN, json("""
                {"schema":"townstead:bond/v2","roles":{
                  "faction":{"party":"faction","gives":["warstead:administer_claim"]},
                  "warden":{"party":"person","requires":"townstead:citizenship"}}}
                """), Map.of()));
        bonds.put(VASSALAGE, BondKind.parse(VASSALAGE, json("""
                {"schema":"townstead:bond/v2","roles":{
                  "liege":{"party":"faction","gives":["townstead:protection"]},
                  "vassal":{"party":"faction","gives":["townstead:land","townstead:allegiance"],"max":1}},
                 "breaking":"grievance"}
                """), Map.of()));
        BondKinds.replaceAll(bonds);
    }

    @AfterEach
    void clearDefinitions() {
        PoliticsFixtures.clear();
    }

    @Test
    void authorityIsScopedToTheFactionItIsAskedAbout() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        join(data, person, A);
        join(data, person, B);
        assertTrue(office(data, WARDEN, A, person).formed());

        PoliticalAuthority.Decision forA = PoliticalAuthority.mayAct(data, person, A, CLAIM);
        PoliticalAuthority.Decision forB = PoliticalAuthority.mayAct(data, person, B, CLAIM);

        assertTrue(forA.allowed());
        assertEquals(WARDEN, forA.grantingBond());
        assertFalse(forB.allowed(), "an office in one faction must not grant power in another");
        assertEquals(id("townstead:missing_capability"), forB.reason());
        assertEquals(id("townstead:no_bond"), PoliticalAuthority.mayAct(data, UUID.randomUUID(), A, CLAIM).reason());
    }

    @Test
    void anOfficeNeedsItsRequiredBondAndEndsWithIt() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();

        assertEquals(FactionBonds.Refusal.REQUIREMENT, office(data, WARDEN, A, person).refusal());
        BondInstance citizenship = join(data, person, A);
        assertTrue(office(data, WARDEN, A, person).formed());

        FactionBonds.end(data, citizenship, 50L, "left");

        assertFalse(FactionBonds.member(data, person, A, WARDEN), "losing citizenship ends the office that required it");
        assertEquals(2, data.bonds(Party.person(person)).size(), "ended bonds are kept as history");
        assertFalse(PoliticalAuthority.mayAct(data, person, A, CLAIM).allowed());
    }

    @Test
    void aBondIsFormedOnceBetweenTheSamePairAndRoleLimitsHold() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        join(data, person, A);

        assertEquals(FactionBonds.Refusal.DUPLICATE, FactionBonds.form(data, CITIZENSHIP,
                FactionBonds.sides(CITIZENSHIP, A, person), id("test:again"), 20L).refusal());

        vassal(data, A, B);
        assertEquals(FactionBonds.Refusal.FULL, vassal(data, A, C).refusal(), "a faction has one parent");
    }

    @Test
    void landRollsUpTheChainOfParentsAndCyclesAreRefused() {
        PoliticalSavedData data = world();
        assertTrue(vassal(data, A, B).formed());
        assertTrue(vassal(data, B, C).formed());

        assertEquals(B, FactionBonds.parent(data, A));
        assertEquals(C, FactionBonds.sovereign(data, A));
        assertNull(FactionBonds.parent(data, C));
        assertEquals(FactionBonds.Refusal.CYCLE, vassal(data, C, A).refusal());
    }

    @Test
    void endingEveryBondOfAPersonReleasesThemEverywhere() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        join(data, person, A);
        join(data, person, B);
        office(data, WARDEN, A, person);

        FactionBonds.endAll(data, Party.person(person), 90L, "reborn");

        assertTrue(data.activeBonds(Party.person(person)).isEmpty());
    }

    @Test
    void aDissolvedFactionCannotFormBonds() {
        PoliticalSavedData data = world();
        data.putFaction(data.faction(A).withStatus(Faction.Status.DISSOLVED));

        assertEquals(FactionBonds.Refusal.INACTIVE_FACTION, FactionBonds.form(data, CITIZENSHIP,
                FactionBonds.sides(CITIZENSHIP, A, UUID.randomUUID()), id("test:join"), 20L).refusal());
    }

    @Test
    void settlementFoundingResultIsStoredSeparatelyFromReloadableDefinitions() {
        PoliticalSavedData data = new PoliticalSavedData();
        SettlementRef settlement = new SettlementRef(id("minecraft:the_nether"), 7);
        data.putFaction(faction(A, "Emberhome", List.of(settlement)));
        SettlementFoundingRecord record = new SettlementFoundingRecord(settlement, id("test:nether_founders"),
                id("test:ember_culture"), id("townstead:village_council"), id("minecraft:crimson_forest"), 12.5F, 80L);

        data.putFounding(record);

        assertEquals(record, data.founding(settlement));
        assertEquals(A, data.faction(settlement).id());
    }

    @Test
    void anOlderSaveBecomesFactionsAndBonds() {
        UUID councilor = UUID.randomUUID(), resident = UUID.randomUUID(), guildsman = UUID.randomUUID(), gone = UUID.randomUUID();
        SettlementRef settlement = new SettlementRef(id("minecraft:overworld"), 12);
        ResourceLocation generation = id("townstead:village_generation");
        var council = new LegacyPolitics.Organization(id("test:council"), id("townstead:village_council"), "Rivercross Council",
                0, null, 5L, generation, "active", settlement);
        var guild = new LegacyPolitics.Organization(id("test:guild"), id("townstead:guild"), "Weavers", 0, null, 5L, generation,
                "active", settlement);
        var polity = new LegacyPolitics.Polity(id("test:rivercross"), "Rivercross", 0, null, 5L, generation, "active",
                List.of(settlement), council.id());
        List<LegacyPolitics.Relation> affiliations = List.of(
                relation("test:res_1", resident, polity.id(), "townstead:residence", "active", Set.of()),
                relation("test:res_2", councilor, polity.id(), "townstead:residence", "active", Set.of()),
                relation("test:res_3", gone, polity.id(), "townstead:residence", "former", Set.of()));
        List<LegacyPolitics.Relation> memberships = List.of(
                relation("test:seat", councilor, council.id(), "townstead:membership", "active",
                        Set.of(id("townstead:councilor"), id("townstead:presiding_councilor"))),
                relation("test:weaver", guildsman, guild.id(), "townstead:membership", "active", Set.of(id("townstead:member"))),
                relation("test:applicant", UUID.randomUUID(), guild.id(), "townstead:membership", "pending", Set.of(id("townstead:member"))));
        var seat = new LegacyPolitics.Seat("polity", polity.id(), settlement, new BlockPos(3, 64, 3), 9, 30L, "", 0L);
        PoliticalSavedData data = new PoliticalSavedData();

        LegacyPolitics.convert(new LegacyPolitics.Input(List.of(council, guild), List.of(polity), affiliations, memberships,
                List.of(seat), List.of(), Map.of(council.id(), 64.0), Set.of()), data);

        Faction village = data.faction(settlement);
        assertEquals(polity.id(), village.id());
        assertEquals(id("townstead:village_council"), village.kind(), "the government's kind becomes the faction's kind");
        assertEquals(village, data.faction(council.id()), "the old government id still finds the faction");
        assertEquals(64.0, data.legitimacy(village.id()));
        assertEquals(village.id(), data.seat(village.id()).faction());
        assertTrue(FactionBonds.member(data, resident, village.id(), CITIZENSHIP));
        assertTrue(FactionBonds.member(data, councilor, village.id(), CITIZENSHIP));
        assertFalse(FactionBonds.member(data, gone, village.id(), CITIZENSHIP), "a former resident's citizenship is kept as ended");
        assertEquals(1, data.bonds(Party.person(gone)).size());
        assertEquals(1, FactionBonds.holders(data, village.id(), CITIZENSHIP).stream().filter(councilor::equals).count(),
                "a councilor who was also a resident is one citizen");
        assertTrue(FactionBonds.member(data, councilor, village.id(), id("townstead:presiding_councilor")));
        assertTrue(FactionBonds.member(data, councilor, village.id(), id("townstead:councilor")));
        Faction weavers = data.faction(guild.id());
        assertTrue(weavers.settlements().isEmpty());
        assertEquals(settlement, weavers.home());
        assertTrue(FactionBonds.member(data, guildsman, weavers.id(), CITIZENSHIP));
        assertEquals(1, FactionBonds.holders(data, weavers.id(), CITIZENSHIP).size(), "a pending application never became a bond");
    }

    private static LegacyPolitics.Relation relation(String id, UUID person, ResourceLocation actor, String kind, String status,
                                                    Set<ResourceLocation> roles) {
        return new LegacyPolitics.Relation(id(id), person, actor, id(kind), status, 10L,
                status.equals("former") ? 20L : BondInstance.ONGOING, id("townstead:village_generation"), roles);
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        data.putFaction(faction(A, "River Wardens", List.of()));
        data.putFaction(faction(B, "Merchant Guild", List.of()));
        data.putFaction(faction(C, "High Crown", List.of()));
        return data;
    }

    private static Faction faction(ResourceLocation id, String name, List<SettlementRef> settlements) {
        return new Faction(id, id("townstead:player_faction"), name, 0xFFFFFF, null, 10L, id("test:generation"),
                Faction.Status.ACTIVE, settlements, null);
    }

    private static BondInstance join(PoliticalSavedData data, UUID person, ResourceLocation faction) {
        return FactionBonds.form(data, CITIZENSHIP, FactionBonds.sides(CITIZENSHIP, faction, person), id("test:join"), 12L).bond();
    }

    private static FactionBonds.Result office(PoliticalSavedData data, ResourceLocation office, ResourceLocation faction, UUID person) {
        return FactionBonds.form(data, office, FactionBonds.sides(office, faction, person), id("test:appointed"), 14L);
    }

    private static FactionBonds.Result vassal(PoliticalSavedData data, ResourceLocation vassal, ResourceLocation liege) {
        return FactionBonds.form(data, VASSALAGE, List.of(new BondInstance.Side("liege", Party.faction(liege)),
                new BondInstance.Side("vassal", Party.faction(vassal))), id("test:sworn"), 16L);
    }
}
