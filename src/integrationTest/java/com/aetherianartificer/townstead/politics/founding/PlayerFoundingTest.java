package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.politics.charter.CharterRequests;
import com.aetherianartificer.townstead.politics.charter.CharterSavedData;
import com.aetherianartificer.townstead.politics.charter.FactionLifecycle;
import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import com.aetherianartificer.townstead.social.BondKind;
import com.aetherianartificer.townstead.social.BondKinds;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises shipped player-founding data, authority, persistence, drafts and dissolution. */
class PlayerFoundingTest {
    @org.junit.jupiter.api.BeforeAll
    static void registerPhenoValues() {
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.StandingValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.VillageNeedsValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(new com.aetherianartificer.townstead.pheno.value.types.VillageSpiritTierValueType());
    }

    @Test
    void foundingLeadershipTransferAndDissolution() throws Exception {
        var chronicle = com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate.parse(
                id("townstead:faction_dissolved"), json("chronicle_event/faction_dissolved"), Map.of());
        assertTrue(chronicle.keep() && chronicle.display().paramNames().equals(List.of("leader", "faction", "settlement")), "Dissolution chronicle lost its history or headline subjects");
        var profile = FoundingProfileDefinition.parse(id("townstead:player_faction"), json("founding_profile/player_faction"), Map.of());
        var kind = FactionKind.parse(id("townstead:player_faction"), json("faction/player_faction"), Map.of());
        Map<ResourceLocation, BondKind> bonds = new HashMap<>();
        for (String name : List.of("citizenship", "faction_leader")) bonds.put(id("townstead:" + name), BondKind.parse(id("townstead:" + name), json("bond/" + name), Map.of()));
        var govern = id("townstead:govern_faction");
        var previousBonds = BondKinds.all();
        var previousKinds = PoliticalDefinitions.snapshot().factionKinds();
        try {
            BondKinds.replaceAll(bonds);
            PoliticalDefinitions.replace(Map.of(kind.id(), kind));
            assertTrue(PoliticalDefinitions.validate(kind, bonds).isEmpty(), "Invalid player faction kind");
            assertTrue(FoundingProfiles.validate(profile).isEmpty(), "Invalid player founding definition");
            assertTrue(profile.weight() == 0, "Player profile entered random NPC generation");
            var data = new PoliticalSavedData(); var founder = UUID.randomUUID(); var outsider = UUID.randomUUID();
            var settlement = new SettlementRef(id("minecraft:overworld"), 222);
            var faction = new Faction(id("test:player_faction"), kind.id(), "Faction", 0, null, 10, profile.id(), Faction.Status.ACTIVE, List.of(settlement), null);
            data.putFaction(faction);
            assertTrue(FoundingProfileApplier.seatFounder(data, faction, kind, founder, 10) == 1, "Founder did not take the leader's office");
            assertTrue(FoundingProfileApplier.seatFounder(data, faction, kind, outsider, 11) == 0, "A second founder took a full office");
            assertTrue(FactionBonds.member(data, founder, faction.id(), id("townstead:faction_leader")), "Founder is not the leader");
            assertTrue(PoliticalAuthority.mayAct(data, founder, faction.id(), govern).allowed(), "Founder cannot govern the faction");
            assertTrue(!PoliticalAuthority.mayAct(data, UUID.randomUUID(), faction.id(), govern).allowed(), "A visitor can govern the faction");
            assertTrue(kind.membership().approval().equals(id("townstead:review_membership_requests")), "Applications are not decided by the leader's capability");
            var saved = new CharterSavedData();
            var proposal = new CharterSavedData.Proposal(UUID.randomUUID(), founder, settlement.dimension(), BlockPos.ZERO,
                    new BlockPos(0, 1, 1), "Village", profile.id(), null, 10, 100);
            saved.prepare(proposal);
            assertTrue(saved.commit(proposal, settlement, faction.id(), 11), "Proclamation did not commit");
            assertTrue(!saved.commit(proposal, settlement, faction.id(), 12), "Proclamation committed twice");
            //? if >=1.21 {
            var restored = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
            var history = CharterSavedData.load(saved.save(new CompoundTag(), null), null);
            //?} else {
            /*var restored = PoliticalSavedData.load(data.save(new CompoundTag()));
            var history = CharterSavedData.load(saved.save(new CompoundTag()));
            *///?}
            assertTrue(PoliticalAuthority.mayAct(restored, founder, faction.id(), govern).allowed(), "Leadership lost after reload");
            assertTrue(history.binding(settlement.dimension(), BlockPos.ZERO).founder().equals(founder), "Historical founder lost after reload");
            var restoredFaction = restored.faction(faction.id());
            assertTrue(!FactionLifecycle.transferHead(restored, restoredFaction, UUID.randomUUID(), 20), "Leadership passed to a nonmember");
            assertTrue(FactionBonds.member(restored, founder, faction.id(), id("townstead:faction_leader")), "A failed transfer still removed the leader");
            FactionBonds.form(restored, FactionBonds.CITIZENSHIP, FactionBonds.sides(FactionBonds.CITIZENSHIP, faction.id(), outsider), id("townstead:charter"), 20);
            assertTrue(FactionLifecycle.transferHead(restored, restoredFaction, outsider, 21), "Leadership transfer failed");
            assertTrue(!PoliticalAuthority.mayAct(restored, founder, faction.id(), govern).allowed(), "Former leader kept authority");
            assertTrue(PoliticalAuthority.mayAct(restored, outsider, faction.id(), govern).allowed(), "New leader lacks authority");
            assertTrue(FactionBonds.member(restored, founder, faction.id(), FactionBonds.CITIZENSHIP), "Transfer removed the former leader's citizenship");

            var requests = new CharterRequests();
            var application = new CharterRequests.Entry(UUID.randomUUID(), faction.id().toString(),
                    UUID.randomUUID(), "Applicant", founder, "application", "pending", FactionBonds.CITIZENSHIP.toString(), "", 0, Set.of());
            requests.put(application);
            assertTrue(FactionLifecycle.dissolve(restored, requests, faction.id(), 30), "Faction could not be dissolved");
            assertTrue(!FactionLifecycle.dissolve(restored, requests, faction.id(), 31), "Dissolution repeated");
            assertTrue(restored.faction(faction.id()).status() == Faction.Status.DISSOLVED, "Faction not archived");
            assertTrue(restored.activeBonds(Party.faction(faction.id())).isEmpty(), "A dissolved faction kept its bonds");
            assertTrue(!requests.get(application.id()).open(), "Application remained open");
            assertTrue(!PoliticalVillageBootstrap.ensure(restored, settlement, "Village", List.of(founder), 40, false, false).available(), "Automatic bootstrap resurrected the faction");
            var replacement = PoliticalVillageBootstrap.ensure(restored, settlement, "Village", List.of(founder), 41, false, true);
            assertTrue(replacement.available() && !replacement.faction().equals(faction.id()), "Refounding reused the historical identity");
            assertTrue(restored.faction(settlement).id().equals(replacement.faction()), "Settlement resolved to the dissolved faction");

            var clause = new CharterSavedData.Clause("dissolve", faction.id().toString(), "", "Faction");
            var draft = new CharterSavedData.Draft(UUID.randomUUID(), faction.id(), founder, settlement.dimension(), BlockPos.ZERO,
                    new BlockPos(0, 1, 1), List.of(clause), Set.of(), CharterSavedData.Draft.UNSIGNED);
            saved.putDraft(draft.signed(founder).preparedUntil(100));
            //? if >=1.21 {
            var pending = CharterSavedData.load(saved.save(new CompoundTag(), null), null);
            //?} else {
            /*var pending = CharterSavedData.load(saved.save(new CompoundTag()));
            *///?}
            var reloaded = pending.draft(faction.id());
            assertTrue(reloaded != null && reloaded.prepared() && reloaded.signatures().contains(founder)
                    && reloaded.clauses().equals(List.of(clause)), "Prepared draft lost on reload");
            assertTrue(pending.draftAtBell(settlement.dimension(), new BlockPos(0, 1, 1)) != null, "Prepared draft is not waiting at its bell");
            assertTrue(pending.removeDraft(faction.id(), reloaded.token()) && !pending.removeDraft(faction.id(), reloaded.token()), "Draft proclaimed twice");
            assertTrue(!draft.withClauses(List.of(clause)).token().equals(draft.token()), "Changing a draft kept its signatures' token");
            saved.expire(100);
            var lapsed = saved.draft(faction.id());
            assertTrue(lapsed != null && !lapsed.prepared() && lapsed.signatures().isEmpty(), "A lapsed draft was lost instead of returning unsigned");
            assertTrue(saved.unbind(faction.id()).size() == 1 && saved.bindings().isEmpty() && saved.draft(faction.id()) == null, "Dissolution left a bell binding or draft");
            assertTrue(saved.archivedBindings().size() == 1 && saved.archivedBindings().get(0).founder().equals(founder), "Dissolution erased founding history");
        } finally {
            BondKinds.replaceAll(previousBonds);
            PoliticalDefinitions.replace(previousKinds);
        }
    }

    private static JsonObject json(String path) throws Exception {
        try (var stream = PlayerFoundingTest.class.getResourceAsStream("/data/townstead/" + path + ".json")) {
            if (stream == null) throw new AssertionError(path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
