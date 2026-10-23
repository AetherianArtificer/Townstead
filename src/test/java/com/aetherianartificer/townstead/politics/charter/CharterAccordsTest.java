package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.definition.PoliticsFixtures;
import com.aetherianartificer.townstead.politics.relations.FactionRelations;
import com.aetherianartificer.townstead.politics.state.BondInstance;
import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.Party;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.aetherianartificer.townstead.politics.definition.PoliticsFixtures.id;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharterAccordsTest {
    private static final ResourceLocation REALM = id("test:realm");
    private static final ResourceLocation TOWN = id("test:town");
    private static final ResourceLocation HAMLET = id("test:hamlet");

    @BeforeEach
    void definitions() {
        PoliticsFixtures.load();
    }

    @AfterEach
    void clear() {
        PoliticsFixtures.clear();
    }

    @Test
    void theHeadOfficeAnswersForItsFaction() {
        PoliticalSavedData data = world();
        UUID leader = UUID.randomUUID();
        UUID presiding = UUID.randomUUID();
        seat(data, REALM, id("townstead:faction_leader"), leader);
        seat(data, TOWN, id("townstead:presiding_councilor"), presiding);

        assertEquals(List.of(leader), people(data, REALM));
        assertEquals(List.of(presiding), people(data, TOWN));
        assertTrue(CharterAccords.representatives(data, data.faction(HAMLET)).isEmpty(),
                "a free settlement has no office that can answer");
    }

    @Test
    void anAccordCanBeOfferedOnceBetweenTwoFactions() {
        PoliticalSavedData data = world();
        Faction realm = data.faction(REALM), town = data.faction(TOWN);
        assertEquals("accord_self", CharterAccords.problem(data, realm, realm));
        assertNull(CharterAccords.problem(data, realm, town));

        List<BondInstance.Side> sides = List.of(new BondInstance.Side("ally", Party.faction(REALM)),
                new BondInstance.Side("ally", Party.faction(TOWN)));
        assertTrue(FactionBonds.form(data, FactionRelations.ACCORD, sides, id("test:letter"), 5L).formed());

        assertEquals("accord_exists", CharterAccords.problem(data, realm, town));
        assertEquals(Set.of(TOWN), CharterAccords.allies(data, realm));
        assertEquals(Set.of(REALM), CharterAccords.allies(data, town));
    }

    private static List<UUID> people(PoliticalSavedData data, ResourceLocation faction) {
        return CharterAccords.representatives(data, data.faction(faction)).stream().map(CharterAccords.Holder::person).toList();
    }

    private static void seat(PoliticalSavedData data, ResourceLocation faction, ResourceLocation office, UUID person) {
        FactionBonds.form(data, FactionBonds.CITIZENSHIP, FactionBonds.sides(FactionBonds.CITIZENSHIP, faction, person), id("test:join"), 1L);
        assertTrue(FactionBonds.form(data, office, FactionBonds.sides(office, faction, person), id("test:office"), 2L).formed());
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        data.putFaction(faction(REALM, "Realm", "townstead:player_faction"));
        data.putFaction(faction(TOWN, "Town", "townstead:village_council"));
        data.putFaction(faction(HAMLET, "Hamlet", "townstead:free_settlement"));
        return data;
    }

    private static Faction faction(ResourceLocation id, String name, String kind) {
        return new Faction(id, id(kind), name, 0xFFFFFF, null, 10L, id("test:generation"),
                Faction.Status.ACTIVE, List.of(), null);
    }
}
