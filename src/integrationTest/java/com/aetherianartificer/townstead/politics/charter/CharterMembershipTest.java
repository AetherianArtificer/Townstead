package com.aetherianartificer.townstead.politics.charter;

import com.aetherianartificer.townstead.politics.definition.*;
import com.aetherianartificer.townstead.politics.state.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline authorization, capacity and durable-request checks using production data classes. */
class CharterMembershipTest {
    @Test
    void membershipAuthorityCapacityAndRequests() throws Exception {
        var role = id("test:officer"); var ordinary = id("test:member");
        var policy = new MembershipPolicyDefinition(id("test:application"),
                new MembershipPolicyDefinition.Admission(id("townstead:application"), null, ctx -> true, List.of(ordinary),
                        new MembershipPolicyDefinition.Decision(id("townstead:role_approval"), role, 2)),
                new MembershipPolicyDefinition.Departure(id("townstead:free_resignation"), null, 0));
        var kind = new OrganizationKindDefinition(id("test:guild"), null, Set.of(), policy.id(),
                List.of(new OrganizationKindDefinition.RoleBinding(role, 0, -1, false),
                        new OrganizationKindDefinition.RoleBinding(ordinary, 0, 1, false)), null, null);
        var field = PoliticalDefinitions.class.getDeclaredField("SNAPSHOT"); field.setAccessible(true);
        Object previous = field.get(null);
        try {
            field.set(null, new PoliticalDefinitions.Snapshot(Map.of(role,
                    new OrganizationRoleDefinition(role, null, Set.of(), OrganizationRoleDefinition.Visibility.PUBLIC), ordinary,
                    new OrganizationRoleDefinition(ordinary, null, Set.of(), OrganizationRoleDefinition.Visibility.PUBLIC)),
                    Map.of(policy.id(), policy), Map.of(kind.id(), kind)));
            var data = new PoliticalSavedData();
            var org = new OrganizationInstance(id("test:group"), kind.id(), policy.id(), "Group", "", 0, null, 0,
                    id("test:source"), PoliticalStatus.Organization.ACTIVE, null);
            var other = new OrganizationInstance(id("test:other"), kind.id(), policy.id(), "Other", "", 0, null, 0,
                    id("test:source"), PoliticalStatus.Organization.ACTIVE, null);
            data.putOrganization(org); data.putOrganization(other);
            UUID officer = UUID.randomUUID(), member = UUID.randomUUID();
            data.putMembership(membership(officer, org, policy, role, "officer"));
            assertTrue(CharterMemberships.deciding(data, officer, org, policy), "Configured approver denied");
            assertTrue(!CharterMemberships.deciding(data, officer, other, policy), "Role leaked across organization scope");
            assertTrue(!CharterMemberships.deciding(data, member, org, policy), "Visitor may approve");
            assertTrue(CharterMemberships.capacity(data, org, policy), "Empty role incorrectly full");
            var joined = membership(member, org, policy, ordinary, "member"); data.putMembership(joined);
            assertTrue(!CharterMemberships.capacity(data, org, policy), "Role maximum exceeded");
            assertTrue(!CharterMemberships.deciding(data, member, org, policy), "Ordinary member may approve");
            CharterMemberships.end(data, joined, 50);
            assertTrue(CharterMemberships.capacity(data, org, policy), "Former membership consumes a place");
            assertTrue(data.membership(joined.id()).affiliation().endedAt() == 50, "Resignation lost end time");
            var rejoined = membership(member, org, policy, ordinary, "replacement"); data.putMembership(rejoined);
            assertTrue(data.membership(member, org.id()).id().equals(rejoined.id()), "Same-tick rejoin selected former membership");
            CharterMemberships.end(data, data.membership(officer, org.id()), 51);
            assertTrue(!CharterMemberships.deciding(data, officer, org, policy), "Former officer still approves");
            var unknown = new MembershipPolicyDefinition(policy.id(), new MembershipPolicyDefinition.Admission(
                    id("modded:ritual"), null, ctx -> true, List.of(),
                    new MembershipPolicyDefinition.Decision(id("modded:decision"), role, 1)), policy.departure());
            assertTrue(!CharterMemberships.deciding(data, officer, org, unknown), "Unknown decision granted authority");

            var settlement = new SettlementRef(id("minecraft:overworld"), 7);
            var polity = new PolityInstance(id("test:polity"), "Polity", 0, null, 0, id("test:source"),
                    PoliticalStatus.Polity.ACTIVE, List.of(settlement), org.id());
            data.putPolity(polity);
            data.putMembership(membership(officer, org, policy, role, "restored_officer"));
            assertTrue(CharterMemberships.deciding(data, officer, org, policy), "Restored officer denied before handover");
            data.markExternalGovernment(polity.id());
            assertTrue(!CharterMemberships.deciding(data, officer, org, policy), "External handover left old officer in charge");
            assertTrue(!data.supersededGovernment(other.id()), "External handover suppressed independent organization");
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
            assertTrue(politicsCopy.supersededGovernment(org.id()), "External authority lost after reload");
            assertTrue(charterCopy.externalGovernment(settlement).equals("mcacapitals:capital-a"), "External capital binding lost after reload");

            var requests = new CharterRequests();
            var entry = new CharterRequests.Entry(UUID.randomUUID(), org.id().toString(), member, "Applicant", member,
                    "application", "pending", policy.id().toString(), "", 0, Set.of());
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
        } finally { field.set(null, previous); }
    }
    private static MembershipInstance membership(UUID person, OrganizationInstance org, MembershipPolicyDefinition policy, ResourceLocation role, String suffix) {
        return new MembershipInstance(new AffiliationInstance(id("test:" + suffix), person, org.actor(), id("townstead:membership"),
                PoliticalStatus.Affiliation.ACTIVE, 1, AffiliationInstance.NOT_ENDED, id("test:source"), PoliticalStatus.Visibility.PUBLIC),
                policy.id(), policy.admission().procedure(), policy.departure().procedure(), Set.of(role));
    }
    private static ResourceLocation id(String value) { return Objects.requireNonNull(ResourceLocation.tryParse(value)); }
}
