package com.aetherianartificer.townstead.politics.relations;

import com.aetherianartificer.townstead.politics.state.Faction;
import com.aetherianartificer.townstead.politics.state.FactionBonds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.root.disposition.DispositionRelations;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionRelationsTest {
    private static final ResourceLocation TOWN = ResourceLocation.tryParse("test:town");
    private static final ResourceLocation COURT = ResourceLocation.tryParse("test:court");

    @BeforeEach
    void definitions() {
        BondKinds.replaceAll(Map.of(FactionBonds.CITIZENSHIP, BondKind.parse(FactionBonds.CITIZENSHIP,
                JsonParser.parseString("""
                        {"schema":"townstead:bond/v2","roles":{
                          "citizen":{"party":"person"},"state":{"party":"faction"}}}
                        """).getAsJsonObject(), Map.of())));
        DispositionRelations.replaceAll(Map.of(
                "wild_vampire", new DispositionRelations.GroupDef(Set.of("vampire", "wild_vampire"), Set.of("townsfolk", "hunter")),
                "hunter", new DispositionRelations.GroupDef(Set.of(), Set.of("vampire", "wild_vampire"))), Map.of());
    }

    @AfterEach
    void clear() {
        BondKinds.replaceAll(Map.of());
        DispositionRelations.replaceAll(Map.of(), Map.of());
    }

    @Test
    void welcomesAreDeclaredAndWithdrawnPerFaction() {
        PoliticalSavedData data = world();
        data.setWelcome(TOWN, "vampire", true);
        assertEquals(Set.of("vampire"), data.welcomes(TOWN));
        assertEquals(Set.of(), data.welcomes(COURT));
        assertTrue(data.anyWelcomes());

        data.setWelcome(TOWN, "vampire", false);
        assertEquals(Set.of(), data.welcomes(TOWN));
        assertFalse(data.anyWelcomes());
    }

    @Test
    void citizensBelongToTheirFactions() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        FactionBonds.form(data, FactionBonds.CITIZENSHIP, FactionBonds.sides(FactionBonds.CITIZENSHIP, TOWN, person),
                ResourceLocation.tryParse("test:join"), 1L);
        assertEquals(Set.of(TOWN), FactionMembership.of(data, person));
        assertEquals(Set.of(), FactionMembership.of(data, UUID.randomUUID()));
    }

    @Test
    void wildVampiresAreKinToVampiresButHuntersAreNot() {
        assertTrue(FactionDispositionSource.kin("vampire", "vampire"));
        assertTrue(FactionDispositionSource.kin("wild_vampire", "vampire"));
        assertFalse(FactionDispositionSource.kin("hunter", "vampire"));
        assertFalse(FactionDispositionSource.kin("townsfolk", "vampire"));
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        data.putFaction(faction(TOWN, "Town"));
        data.putFaction(faction(COURT, "Court"));
        return data;
    }

    private static Faction faction(ResourceLocation id, String name) {
        return new Faction(id, ResourceLocation.tryParse("townstead:player_faction"), name, 0xFFFFFF, null, 10L,
                ResourceLocation.tryParse("test:generation"), Faction.Status.ACTIVE, List.of(), null);
    }
}
