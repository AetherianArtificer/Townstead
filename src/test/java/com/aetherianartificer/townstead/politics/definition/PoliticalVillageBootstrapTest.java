package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalIds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalVillageBootstrapTest {
    private static final ResourceLocation COUNCILOR = id("townstead:councilor");
    private static final ResourceLocation PRESIDING = id("townstead:presiding_councilor");
    private static final ResourceLocation CITIZENSHIP = FactionBonds.CITIZENSHIP;
    private static final SettlementRef RIVERCROSS = new SettlementRef(id("minecraft:overworld"), 7);

    @BeforeEach
    void definitions() {
        PoliticsFixtures.load();
    }

    @AfterEach
    void clearDefinitions() {
        PoliticsFixtures.clear();
    }

    @Test
    void firstRecognitionCreatesOneFactionItsCitizensAndItsCouncil() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = residents(4);

        PoliticalVillageBootstrap.Result result = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross", residents, 40L);

        assertTrue(result.available());
        assertTrue(result.createdFaction());
        assertEquals(PoliticalIds.villageFaction(RIVERCROSS), result.faction());
        assertEquals(4, result.citizensAdded());
        assertEquals(1, data.factions().size());
        assertEquals(PoliticalVillageBootstrap.VILLAGE_COUNCIL, data.faction(result.faction()).kind());
        assertEquals(4, FactionBonds.holders(data, result.faction(), CITIZENSHIP).size());
        assertEquals(1, FactionBonds.holders(data, result.faction(), PRESIDING).size());
        assertTrue(FactionBonds.holders(data, result.faction(), COUNCILOR).size() >= 2, "the council starts at least at its minimum");
        UUID first = residents.stream().min(Comparator.comparing(UUID::toString)).orElseThrow();
        assertTrue(FactionBonds.member(data, first, result.faction(), PRESIDING));
    }

    @Test
    void repeatedRecognitionKeepsTheSameFactionAndCouncil() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = residents(3);
        PoliticalVillageBootstrap.Result first = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross", residents, 40L);
        Set<ResourceLocation> bonds = data.bonds().stream().map(BondInstance::id).collect(Collectors.toSet());
        List<UUID> reversed = new ArrayList<>(residents);
        java.util.Collections.reverse(reversed);

        PoliticalVillageBootstrap.Result second = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "A Renamed Rivercross", reversed, 80L);

        assertEquals(first.faction(), second.faction());
        assertFalse(second.createdFaction());
        assertEquals(0, second.citizensAdded());
        assertEquals(0, second.officesFilled());
        assertEquals(bonds, data.bonds().stream().map(BondInstance::id).collect(Collectors.toSet()));
        assertEquals("Rivercross", data.faction(first.faction()).name(), "a generated faction does not rename itself on a scan");
    }

    @Test
    void aSettlementWithoutGovernmentHasCitizensButNoOffices() {
        PoliticalSavedData data = new PoliticalSavedData();

        PoliticalVillageBootstrap.Result result = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross",
                residents(2), 40L, false, false);

        Faction faction = data.faction(result.faction());
        assertEquals(PoliticalVillageBootstrap.FREE_SETTLEMENT, faction.kind());
        assertEquals(2, FactionBonds.holders(data, faction.id(), CITIZENSHIP).size());
        assertEquals(2, data.bonds().size());
    }

    @Test
    void movingToANewHomeEndsTheOldCitizenship() {
        PoliticalSavedData data = new PoliticalSavedData();
        UUID resident = UUID.randomUUID();
        PoliticalVillageBootstrap.Result oldHome = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross", List.of(resident), 40L);
        SettlementRef hilltop = new SettlementRef(id("minecraft:overworld"), 8);

        PoliticalVillageBootstrap.Result newHome = PoliticalVillageBootstrap.ensure(data, hilltop, "Hilltop", List.of(resident), 90L);

        List<BondInstance> citizenships = data.bonds(Party.person(resident)).stream()
                .filter(bond -> bond.kind().equals(CITIZENSHIP)).toList();
        assertEquals(2, citizenships.size());
        assertTrue(citizenships.stream().anyMatch(bond -> bond.active() && bond.involves(Party.faction(newHome.faction()))));
        assertTrue(citizenships.stream().anyMatch(bond -> !bond.active() && bond.involves(Party.faction(oldHome.faction()))
                && bond.endedAt() == 90L));
        assertFalse(FactionBonds.member(data, resident, oldHome.faction(), PRESIDING), "leaving ends the offices held there");
    }

    @Test
    void aCouncilorWhoIsGoneIsReplacedByAResident() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = residents(4);
        PoliticalVillageBootstrap.Result result = PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross", residents, 40L);
        UUID presiding = FactionBonds.holders(data, result.faction(), PRESIDING).get(0);
        List<UUID> survivors = residents.stream().filter(person -> !person.equals(presiding)).toList();

        PoliticalVillageBootstrap.ensure(data, RIVERCROSS, "Rivercross", survivors, 120L);

        assertFalse(FactionBonds.member(data, presiding, result.faction(), CITIZENSHIP));
        assertFalse(FactionBonds.member(data, presiding, result.faction(), PRESIDING), "no ghost council: the dead hold no office");
        List<UUID> chair = FactionBonds.holders(data, result.faction(), PRESIDING);
        assertEquals(1, chair.size());
        assertTrue(survivors.contains(chair.get(0)));
        assertTrue(FactionBonds.holders(data, result.faction(), COUNCILOR).size() >= 2);
    }

    private static List<UUID> residents(int count) {
        List<UUID> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(UUID.randomUUID());
        return out;
    }
}
