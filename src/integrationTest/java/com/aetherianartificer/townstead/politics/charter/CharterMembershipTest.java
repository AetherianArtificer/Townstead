package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline approval authority, external government and durable requests using production data classes. */
class CharterMembershipTest {
    @Test
    void approvalAuthorityExternalGovernmentAndRequests() {
        var review = id("test:review");
        var officerBond = BondKind.parse(id("test:officer"), JsonParser.parseString("""
                {"schema":"townstead:bond/v2","roles":{
                  "faction":{"party":"faction","gives":["test:review"]},
                  "officer":{"party":"person","requires":"townstead:citizenship"}}}
                """).getAsJsonObject(), Map.of());
        var citizenship = BondKind.parse(FactionBonds.CITIZENSHIP, JsonParser.parseString("""
                {"schema":"townstead:bond/v2","roles":{
                  "citizen":{"party":"person","visibility":"members"},
                  "state":{"party":"faction"}}}
                """).getAsJsonObject(), Map.of());
        var kind = FactionKind.parse(id("test:guild"), JsonParser.parseString("""
                {"schema":"townstead:faction/v1","holds_land":false,
                 "membership":{"bond":"townstead:citizenship","admission":"townstead:application","approval":"test:review","approvals":2},
                 "offices":[{"bond":"test:officer"}]}
                """).getAsJsonObject(), Map.of());
        var previousBonds = BondKinds.all();
        var previousKinds = PoliticalDefinitions.snapshot().factionKinds();
        try {
            BondKinds.replaceAll(Map.of(officerBond.id(), officerBond, citizenship.id(), citizenship));
            PoliticalDefinitions.replace(Map.of(kind.id(), kind));
            var settlement = new SettlementRef(id("minecraft:overworld"), 7);
            var data = new PoliticalSavedData();
            var guild = new Faction(id("test:group"), kind.id(), "Group", 0, null, 0, id("test:source"), Faction.Status.ACTIVE, List.of(settlement), null);
            var other = new Faction(id("test:other"), kind.id(), "Other", 0, null, 0, id("test:source"), Faction.Status.ACTIVE, List.of(), null);
            data.putFaction(guild); data.putFaction(other);
            UUID officer = UUID.randomUUID(), member = UUID.randomUUID();
            join(data, officer, guild); join(data, officer, other); join(data, member, guild);
            var office = FactionBonds.form(data, officerBond.id(), FactionBonds.sides(officerBond.id(), guild.id(), officer), id("test:source"), 2).bond();
            assertTrue(CharterMemberships.deciding(data, officer, guild, kind), "Configured approver denied");
            assertTrue(!CharterMemberships.deciding(data, officer, other, kind), "An office leaked across factions");
            assertTrue(!CharterMemberships.deciding(data, member, guild, kind), "An ordinary member may approve");
            assertTrue(!CharterMemberships.deciding(data, UUID.randomUUID(), guild, kind), "A visitor may approve");
            FactionBonds.end(data, office, 50, "resigned");
            assertTrue(!CharterMemberships.deciding(data, officer, guild, kind), "A former officer still approves");
            FactionBonds.form(data, officerBond.id(), FactionBonds.sides(officerBond.id(), guild.id(), officer), id("test:source"), 60);
            assertTrue(CharterMemberships.deciding(data, officer, guild, kind), "A reappointed officer is denied");
            data.markExternalGovernment(guild.id());
            assertTrue(!CharterMemberships.deciding(data, officer, guild, kind), "External government left the old officer in charge");
            assertTrue(!data.externalGovernment(other.id()), "External government reached an unrelated faction");
            var charters = new CharterSavedData();
            charters.observeGovernment(settlement, "mcacapitals:capital-a");
            assertTrue(charters.externalGovernment(new SettlementRef(id("minecraft:the_nether"), 7)).isEmpty(), "Village ID crossed dimensions");
            //? if >=1.21 {
            var politicsCopy = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
            var charterCopy = CharterSavedData.load(charters.save(new CompoundTag(), null), null);
            //?} else {
            /*var politicsCopy = PoliticalSavedData.load(data.save(new CompoundTag()));
            var charterCopy = CharterSavedData.load(charters.save(new CompoundTag()));
            *///?}
            assertTrue(politicsCopy.externalGovernment(guild.id()), "External authority lost after reload");
            assertTrue(FactionBonds.member(politicsCopy, officer, guild.id(), officerBond.id()), "Office lost after reload");
            assertTrue(politicsCopy.bonds(Party.person(officer)).size() == data.bonds(Party.person(officer)).size(), "Bond history lost after reload");
            assertTrue(charterCopy.externalGovernment(settlement).equals("mcacapitals:capital-a"), "External capital binding lost after reload");

            var requests = new CharterRequests();
            var entry = new CharterRequests.Entry(UUID.randomUUID(), guild.id().toString(), member, "Applicant", member,
                    "application", "pending", FactionBonds.CITIZENSHIP.toString(), "", 0, Set.of());
            entry = entry.approve(officer).approve(officer);
            assertTrue(entry.approvals().size() == 1, "Duplicate vote counted twice");
            requests.put(entry);
            assertTrue(requests.revision() == 1 && entry.open(), "New request revision/state incorrect");
            //? if >=1.21 {
            var copy = CharterRequests.load(requests.save(new CompoundTag(), null), null);
            //?} else {
            /*var copy = CharterRequests.load(requests.save(new CompoundTag()));
            *///?}
            assertTrue(copy.entries().equals(requests.entries()) && copy.revision() == requests.revision(), "Saved request changed after reload");
            copy.put(entry.state("withdrawn"));
            assertTrue(copy.revision() == 2 && !copy.get(entry.id()).open(), "Withdrawal remained actionable");
        } finally {
            BondKinds.replaceAll(previousBonds);
            PoliticalDefinitions.replace(previousKinds);
        }
    }

    private static void join(PoliticalSavedData data, UUID person, Faction faction) {
        FactionBonds.form(data, FactionBonds.CITIZENSHIP, FactionBonds.sides(FactionBonds.CITIZENSHIP, faction.id(), person), id("test:source"), 1);
    }

    private static ResourceLocation id(String value) { return Objects.requireNonNull(ResourceLocation.tryParse(value)); }
}
