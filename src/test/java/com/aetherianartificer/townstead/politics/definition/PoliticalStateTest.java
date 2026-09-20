package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.OrganizationInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalActorRef;
import com.aetherianartificer.townstead.politics.state.PoliticalAuthority;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SettlementFoundingRecord;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalStateTest {
    private static final ResourceLocation CLAIM = id("warstead:administer_claim");
    private static final ResourceLocation ADMIN = id("test:claim_admin");
    private static final ResourceLocation MEMBER = id("townstead:member");
    private static final ResourceLocation ORG_A = id("test:river_wardens");
    private static final ResourceLocation ORG_B = id("test:merchant_guild");
    private static final ResourceLocation POLITY = id("test:rivercross");

    @BeforeEach
    void definitions() {
        OrganizationRoleDefinition admin = OrganizationRoleDefinition.parse(ADMIN,
                JsonParser.parseString("""
                        {
                          "schema":"townstead:organization_role/v1",
                          "capabilities":["warstead:administer_claim"]
                        }
                        """).getAsJsonObject(), Map.of());
        OrganizationRoleDefinition member = OrganizationRoleDefinition.parse(MEMBER,
                JsonParser.parseString("""
                        {
                          "schema":"townstead:organization_role/v1",
                          "capabilities":[]
                        }
                        """).getAsJsonObject(), Map.of());
        PoliticalDefinitions.replace(Map.of(ADMIN, admin, MEMBER, member), Map.of(), Map.of());
    }

    @AfterEach
    void clearDefinitions() {
        PoliticalDefinitions.replace(Map.of(), Map.of(), Map.of());
    }

    @Test
    void authorityIsScopedToTheExplicitActingActor() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        data.putMembership(membership("test:membership_a", person, ORG_A,
                PoliticalStatus.Affiliation.ACTIVE, ADMIN));
        data.putMembership(membership("test:membership_b", person, ORG_B,
                PoliticalStatus.Affiliation.ACTIVE, MEMBER));

        PoliticalAuthority.Decision forA = PoliticalAuthority.mayAct(data, person,
                organization(ORG_A), CLAIM);
        PoliticalAuthority.Decision forB = PoliticalAuthority.mayAct(data, person,
                organization(ORG_B), CLAIM);

        assertTrue(forA.allowed());
        assertEquals(ADMIN, forA.grantingRole());
        assertFalse(forB.allowed(), "a role in one organization must not grant power in another");
        assertEquals(id("townstead:missing_capability"), forB.reason());
    }

    @Test
    void polityAuthorityDelegatesOnlyThroughItsBoundGovernment() {
        PoliticalSavedData data = world();
        UUID governor = UUID.randomUUID(), outsider = UUID.randomUUID();
        data.putMembership(membership("test:governor", governor, ORG_A,
                PoliticalStatus.Affiliation.ACTIVE, ADMIN));
        data.putMembership(membership("test:outsider", outsider, ORG_B,
                PoliticalStatus.Affiliation.ACTIVE, ADMIN));

        assertTrue(PoliticalAuthority.mayAct(data, governor, polity(POLITY), CLAIM).allowed());
        assertFalse(PoliticalAuthority.mayAct(data, outsider, polity(POLITY), CLAIM).allowed());
    }

    @Test
    void suspendedMembershipRetainsHistoryButGrantsNothing() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        data.putMembership(membership("test:suspended", person, ORG_A,
                PoliticalStatus.Affiliation.SUSPENDED, ADMIN));

        assertEquals(1, data.memberships(person).size());
        PoliticalAuthority.Decision result = PoliticalAuthority.mayAct(data, person,
                organization(ORG_A), CLAIM);
        assertFalse(result.allowed());
        assertEquals(id("townstead:inactive_membership"), result.reason());
    }

    @Test
    void onePersonMayHoldMembershipsInSeveralOrganizations() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        data.putMembership(membership("test:first", person, ORG_A,
                PoliticalStatus.Affiliation.ACTIVE, ADMIN));
        data.putMembership(membership("test:second", person, ORG_B,
                PoliticalStatus.Affiliation.ACTIVE, MEMBER));
        data.putAffiliation(new AffiliationInstance(id("test:resident"), person, polity(POLITY),
                id("townstead:residence"), PoliticalStatus.Affiliation.ACTIVE, 10L,
                AffiliationInstance.NOT_ENDED, id("townstead:generation"), PoliticalStatus.Visibility.PUBLIC));

        assertEquals(2, data.memberships(person).size());
        assertEquals(3, data.affiliations(person).size());
        assertThrows(IllegalArgumentException.class, () -> data.putMembership(
                membership("test:duplicate", person, ORG_A, PoliticalStatus.Affiliation.ACTIVE, MEMBER)));
    }

    @Test
    void formerMembershipRemainsWhileARealRejoiningCreatesANewPeriod() {
        PoliticalSavedData data = world();
        UUID person = UUID.randomUUID();
        AffiliationInstance former = new AffiliationInstance(id("test:former"), person, organization(ORG_A),
                id("townstead:membership"), PoliticalStatus.Affiliation.FORMER, 2L, 8L,
                id("townstead:application"), PoliticalStatus.Visibility.PUBLIC);
        data.putMembership(new MembershipInstance(former, id("townstead:application"),
                id("townstead:application"), id("townstead:free_resignation"), Set.of(MEMBER)));
        data.putMembership(membership("test:returned", person, ORG_A,
                PoliticalStatus.Affiliation.ACTIVE, ADMIN));

        assertEquals(2, data.memberships(person).size());
        assertEquals(id("test:returned"), data.membership(person, ORG_A).id());
        assertTrue(PoliticalAuthority.mayAct(data, person, organization(ORG_A), CLAIM).allowed());
    }

    @Test
    void settlementFoundingResultIsStoredSeparatelyFromReloadableDefinitions() {
        PoliticalSavedData data = new PoliticalSavedData();
        SettlementRef settlement = new SettlementRef(id("minecraft:the_nether"), 7);
        data.putOrganization(organizationInstance(ORG_A, "Ember Council"));
        data.putPolity(new PolityInstance(POLITY, "Emberhome", 0xAA4411, null, 10L,
                id("townstead:generation"), PoliticalStatus.Polity.ACTIVE, List.of(settlement), ORG_A));
        SettlementFoundingRecord record = new SettlementFoundingRecord(settlement,
                id("test:nether_founders"), id("test:ember_culture"), ORG_A,
                id("minecraft:crimson_forest"), 12.5F, 80L);

        data.putFounding(record);

        assertEquals(record, data.founding(settlement));
        assertEquals(1, data.foundingRecords().size());
    }

    @Test
    void governmentFreeFoundingRequiresNoFictionalOrganization() {
        PoliticalSavedData data = new PoliticalSavedData();
        SettlementRef settlement = new SettlementRef(id("minecraft:overworld"), 19);
        data.putPolity(new PolityInstance(POLITY, "Freebank", 0x668844, null, 10L,
                id("townstead:charter"), PoliticalStatus.Polity.ACTIVE, List.of(settlement), null));
        SettlementFoundingRecord record = new SettlementFoundingRecord(settlement,
                id("townstead:no_formal_government"), null, null, null, 0.0F, 80L);

        data.putFounding(record);

        assertEquals(record, data.founding(settlement));
        assertEquals(null, data.polity(POLITY).governmentOrganization());
        assertTrue(data.organizations().isEmpty());
    }

    private static PoliticalSavedData world() {
        PoliticalSavedData data = new PoliticalSavedData();
        data.putOrganization(organizationInstance(ORG_A, "River Wardens"));
        data.putOrganization(organizationInstance(ORG_B, "Merchant Guild"));
        data.putPolity(new PolityInstance(POLITY, "Rivercross", 0x336699, null, 10L,
                id("townstead:generation"), PoliticalStatus.Polity.ACTIVE, List.of(), ORG_A));
        return data;
    }

    private static OrganizationInstance organizationInstance(ResourceLocation id, String name) {
        return new OrganizationInstance(id, id("townstead:civic_faction"), id("townstead:application"),
                name, name, 0xFFFFFF, null, 10L, id("townstead:generation"),
                PoliticalStatus.Organization.ACTIVE, null);
    }

    private static MembershipInstance membership(String id, UUID person, ResourceLocation organization,
                                                 PoliticalStatus.Affiliation status, ResourceLocation role) {
        AffiliationInstance affiliation = new AffiliationInstance(id(id), person, organization(organization),
                id("townstead:membership"), status, 12L, AffiliationInstance.NOT_ENDED,
                id("townstead:application"), PoliticalStatus.Visibility.PUBLIC);
        return new MembershipInstance(affiliation, id("townstead:application"),
                id("townstead:application"), id("townstead:free_resignation"), Set.of(role));
    }

    private static PoliticalActorRef organization(ResourceLocation id) {
        return new PoliticalActorRef(PoliticalActorRef.Kind.ORGANIZATION, id);
    }

    private static PoliticalActorRef polity(ResourceLocation id) {
        return new PoliticalActorRef(PoliticalActorRef.Kind.POLITY, id);
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }
}
