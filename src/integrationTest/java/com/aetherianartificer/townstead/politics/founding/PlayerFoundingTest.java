package com.aetherianartificer.townstead.politics.founding;

import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import com.aetherianartificer.townstead.politics.charter.CharterSavedData;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises shipped player-founding data, authority, persistence and single-use proclamation. */
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
        var kind = OrganizationKindDefinition.parse(id("townstead:player_faction"), json("organization_kind/player_faction"), Map.of());
        var policy = MembershipPolicyDefinition.parse(id("townstead:player_faction"), json("membership_policy/player_faction"));
        var leader = OrganizationRoleDefinition.parse(id("townstead:faction_leader"), json("organization_role/faction_leader"), Map.of());
        var member = OrganizationRoleDefinition.parse(id("townstead:member"), json("organization_role/member"), Map.of());
        var field = PoliticalDefinitions.class.getDeclaredField("SNAPSHOT"); field.setAccessible(true);
        var previous = field.get(null);
        try {
            field.set(null, new PoliticalDefinitions.Snapshot(Map.of(leader.id(), leader, member.id(), member), Map.of(policy.id(), policy), Map.of(kind.id(), kind)));
            assertTrue(FoundingProfiles.validate(profile).isEmpty(), "Invalid player founding definition");
            assertTrue(profile.weight() == 0, "Player profile entered random NPC generation");
            var data = new PoliticalSavedData(); var founder = UUID.randomUUID(); var outsider = UUID.randomUUID();
            var settlement = new SettlementRef(id("minecraft:overworld"), 222);
            var org = new OrganizationInstance(id("test:leadership"), kind.id(), policy.id(), "Faction", "Faction", 0, null, 10,
                    profile.id(), PoliticalStatus.Organization.ACTIVE, settlement);
            data.putOrganization(org);
            var polity = new PolityInstance(id("test:player_faction"), "Faction", 0, null, 10, profile.id(), PoliticalStatus.Polity.ACTIVE, List.of(settlement), org.id());
            data.putPolity(polity);
            assertTrue(FoundingProfileApplier.seedGovernment(data, org, kind, policy, profile, List.of(founder), 10) == 1, "Founder did not receive membership");
            assertTrue(FoundingProfileApplier.seedGovernment(data, org, kind, policy, profile, List.of(outsider), 11) == 0, "Repeated founding reassigned leadership");
            assertTrue(data.membership(founder, org.id()).roles().contains(leader.id()), "Founder has no leadership role");
            assertTrue(PoliticalAuthority.mayAct(data, founder, polity.actor(), id("townstead:govern_polity")).allowed(), "Founder cannot manage faction");
            assertTrue(!PoliticalAuthority.mayAct(data, outsider, polity.actor(), id("townstead:govern_polity")).allowed(), "Outsider can manage faction");
            assertTrue(policy.admission().decision().role().equals(leader.id()), "Applications are not decided by the leader");
            var saved = new CharterSavedData();
            var proposal = new CharterSavedData.Proposal(UUID.randomUUID(), founder, settlement.dimension(), BlockPos.ZERO,
                    new BlockPos(0, 1, 1), "Village", profile.id(), null, 10, 100);
            saved.prepare(proposal);
            assertTrue(saved.commit(proposal, settlement, polity.id(), 11), "Proclamation did not commit");
            assertTrue(!saved.commit(proposal, settlement, polity.id(), 12), "Proclamation committed twice");
            //? if >=1.21 {
            var restored = PoliticalSavedData.load(data.save(new CompoundTag(), null), null);
            var history = CharterSavedData.load(saved.save(new CompoundTag(), null), null);
            //?} else {
            /*var restored = PoliticalSavedData.load(data.save(new CompoundTag()));
            var history = CharterSavedData.load(saved.save(new CompoundTag()));
            *///?}
            assertTrue(PoliticalAuthority.mayAct(restored, founder, polity.actor(), id("townstead:govern_polity")).allowed(), "Leadership lost after reload");
            assertTrue(history.binding(settlement.dimension(), BlockPos.ZERO).founder().equals(founder), "Historical founder lost after reload");
            assertTrue(!com.aetherianartificer.townstead.politics.charter.CharterMemberships.transferLeadership(restored, org, founder, outsider), "Transferred to a nonmember");
            var affiliation = new AffiliationInstance(id("test:member"), outsider, org.actor(), id("townstead:membership"),
                    PoliticalStatus.Affiliation.ACTIVE, 20, AffiliationInstance.NOT_ENDED, profile.id(), PoliticalStatus.Visibility.MEMBERS);
            restored.putMembership(new MembershipInstance(affiliation, policy.id(), policy.admission().procedure(), policy.departure().procedure(), Set.of(member.id())));
            assertTrue(!com.aetherianartificer.townstead.politics.charter.CharterMemberships.transferLeadership(restored, org, outsider, founder), "Nonleader transferred authority");
            assertTrue(!com.aetherianartificer.townstead.politics.charter.CharterMemberships.transferLeadership(restored, org, founder, founder), "Self transfer accepted");
            assertTrue(com.aetherianartificer.townstead.politics.charter.CharterMemberships.transferLeadership(restored, org, founder, outsider), "Leadership transfer failed");
            assertTrue(!PoliticalAuthority.mayAct(restored, founder, polity.actor(), id("townstead:govern_polity")).allowed(), "Former leader retained authority");
            assertTrue(PoliticalAuthority.mayAct(restored, outsider, polity.actor(), id("townstead:govern_polity")).allowed(), "New leader lacks authority");
            assertTrue(restored.membership(founder, org.id()).affiliation().active(), "Transfer removed former leader membership");
            assertTrue(history.binding(settlement.dimension(), BlockPos.ZERO).founder().equals(founder), "Transfer rewrote founder history");
            var requests = new com.aetherianartificer.townstead.politics.charter.CharterRequests();
            var application = new com.aetherianartificer.townstead.politics.charter.CharterRequests.Entry(UUID.randomUUID(), org.id().toString(),
                    UUID.randomUUID(), "Applicant", founder, "application", "pending", policy.id().toString(), "", 0, Set.of());
            requests.put(application);
            assertTrue(!com.aetherianartificer.townstead.politics.charter.FactionLifecycle.dissolve(restored, requests, polity.id(), founder, 30), "Former leader dissolved faction");
            assertTrue(com.aetherianartificer.townstead.politics.charter.FactionLifecycle.dissolve(restored, requests, polity.id(), outsider, 30), "Leader could not dissolve faction");
            assertTrue(!com.aetherianartificer.townstead.politics.charter.FactionLifecycle.dissolve(restored, requests, polity.id(), outsider, 31), "Dissolution repeated");
            assertTrue(restored.polity(polity.id()).status() == PoliticalStatus.Polity.DISSOLVED, "Faction not archived");
            assertTrue(restored.memberships(org.actor()).stream().noneMatch(m -> m.affiliation().active()), "Dissolved membership still active");
            assertTrue(!requests.get(application.id()).open(), "Application remained open");
            assertTrue(!PoliticalVillageBootstrap.ensure(restored, settlement, "Village", List.of(founder), 40, false).available(), "Automatic bootstrap resurrected faction");
            var replacement = PoliticalVillageBootstrap.ensure(restored, settlement, "Village", List.of(founder), 41, false, true);
            assertTrue(replacement.available() && !replacement.polity().equals(polity.id()), "Refounding reused historical identity");
            assertTrue(restored.polity(settlement).id().equals(replacement.polity()), "Settlement resolved to dissolved faction");
            var amendment = new CharterSavedData.Amendment(UUID.randomUUID(), founder, polity.id(), settlement.dimension(), BlockPos.ZERO,
                    new BlockPos(0, 1, 1), "dissolve_faction", "Faction", "", 100);
            saved.prepareAmendment(amendment);
            //? if >=1.21 {
            var pending = CharterSavedData.load(saved.save(new CompoundTag(), null), null);
            //?} else {
            /*var pending = CharterSavedData.load(saved.save(new CompoundTag()));
            *///?}
            assertTrue(amendment.equals(pending.amendment(polity.id())), "Pending amendment lost on reload");
            assertTrue(pending.removeAmendment(amendment) && !pending.removeAmendment(amendment), "Amendment consumed twice");
            saved.expire(100); assertTrue(saved.amendment(polity.id()) == null, "Expired amendment survived");
            assertTrue(saved.unbind(polity.id()).size() == 1 && saved.bindings().isEmpty(), "Dissolution left a bell binding");
            assertTrue(saved.archivedBindings().size() == 1 && saved.archivedBindings().get(0).founder().equals(founder), "Dissolution erased founding history");
        } finally { field.set(null, previous); }
    }
    private static JsonObject json(String path) throws Exception {
        try (var stream = PlayerFoundingTest.class.getResourceAsStream("/data/townstead/" + path + ".json")) {
            if (stream == null) throw new AssertionError(path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    private static ResourceLocation id(String value) { return ResourceLocation.tryParse(value); }
}
