package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.politics.state.AffiliationInstance;
import com.aetherianartificer.townstead.politics.state.MembershipInstance;
import com.aetherianartificer.townstead.politics.state.PoliticalIds;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalVillageBootstrap;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalVillageBootstrapTest {
    private static final ResourceLocation COUNCILOR = id("townstead:councilor");
    private static final ResourceLocation PRESIDING = id("townstead:presiding_councilor");
    private static final ResourceLocation RESIDENCE = id("townstead:residence");
    private static final SettlementRef RIVERCROSS = new SettlementRef(id("minecraft:overworld"), 7);

    @BeforeEach
    void definitions() {
        OrganizationRoleDefinition councilor = OrganizationRoleDefinition.parse(COUNCILOR,
                JsonParser.parseString("""
                        {"schema":"townstead:organization_role/v1","capabilities":["townstead:govern_polity"]}
                        """).getAsJsonObject(), Map.of());
        OrganizationRoleDefinition presiding = OrganizationRoleDefinition.parse(PRESIDING,
                JsonParser.parseString("""
                        {"schema":"townstead:organization_role/v1","capabilities":["townstead:represent_polity"]}
                        """).getAsJsonObject(), Map.of());
        MembershipPolicyDefinition policy = MembershipPolicyDefinition.parse(id("townstead:appointed_council"),
                JsonParser.parseString("""
                        {
                          "schema":"townstead:membership_policy/v1",
                          "admission":{
                            "procedure":"townstead:appointment",
                            "initial_roles":["townstead:councilor"],
                            "decision":{"procedure":"townstead:role_approval","role":"townstead:presiding_councilor"}
                          },
                          "departure":{"procedure":"townstead:notice","notice_days":1}
                        }
                        """).getAsJsonObject());
        OrganizationKindDefinition kind = OrganizationKindDefinition.parse(id("townstead:village_council"),
                JsonParser.parseString("""
                        {
                          "schema":"townstead:organization_kind/v1",
                          "membership_policy":"townstead:appointed_council",
                          "roles":[
                            {"role":"townstead:presiding_councilor","min":1,"max":1},
                            {"role":"townstead:councilor","min":1,"max":8}
                          ],
                          "founding":{"procedure":"townstead:generated_village_government"}
                        }
                        """).getAsJsonObject(), Map.of());
        PoliticalDefinitions.replace(Map.of(COUNCILOR, councilor, PRESIDING, presiding),
                Map.of(policy.id(), policy), Map.of(kind.id(), kind));
    }

    @AfterEach
    void clearDefinitions() {
        PoliticalDefinitions.replace(Map.of(), Map.of(), Map.of());
    }

    @Test
    void firstRecognitionCreatesOnePolityGovernmentResidencesAndCouncil() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        PoliticalVillageBootstrap.Result result = PoliticalVillageBootstrap.ensure(
                data, RIVERCROSS, "Rivercross", residents, 40L);

        assertTrue(result.available());
        assertTrue(result.createdPolity());
        assertTrue(result.createdGovernment());
        assertEquals(4, result.residencesCreated());
        assertEquals(3, result.councilorsCreated());
        assertEquals(1, data.polities().size());
        assertEquals(1, data.organizations().size());
        assertEquals(4, data.directAffiliations().size());
        assertEquals(3, data.memberships().size());
        assertEquals(PoliticalIds.villagePolity(RIVERCROSS), result.polity());
        assertEquals(PoliticalIds.villageCouncil(RIVERCROSS), result.government());

        UUID first = residents.stream().min(Comparator.comparing(UUID::toString)).orElseThrow();
        MembershipInstance chair = data.membership(first, result.government());
        assertNotNull(chair);
        assertEquals(Set.of(COUNCILOR, PRESIDING), chair.roles());
    }

    @Test
    void repeatedRecognitionRecoversTheSameIdentityWithoutReselectingGovernment() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        PoliticalVillageBootstrap.Result first = PoliticalVillageBootstrap.ensure(
                data, RIVERCROSS, "Rivercross", residents, 40L);
        Set<ResourceLocation> originalMemberships = data.memberships().stream()
                .map(MembershipInstance::id).collect(java.util.stream.Collectors.toSet());
        List<UUID> reversed = new java.util.ArrayList<>(residents);
        java.util.Collections.reverse(reversed);

        PoliticalVillageBootstrap.Result second = PoliticalVillageBootstrap.ensure(
                data, RIVERCROSS, "A Renamed Rivercross", reversed, 80L);

        assertEquals(first.polity(), second.polity());
        assertEquals(first.government(), second.government());
        assertFalse(second.createdPolity());
        assertFalse(second.createdGovernment());
        assertEquals(0, second.residencesCreated());
        assertEquals(0, second.councilorsCreated());
        assertEquals(originalMemberships, data.memberships().stream()
                .map(MembershipInstance::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals("Rivercross", data.polity(first.polity()).name(), "generated identity does not rename itself on a scan");
    }

    @Test
    void explicitGovernmentFreeBootstrapCreatesResidencesButNoCouncil() {
        PoliticalSavedData data = new PoliticalSavedData();
        List<UUID> residents = List.of(UUID.randomUUID(), UUID.randomUUID());

        PoliticalVillageBootstrap.Result result = PoliticalVillageBootstrap.ensure(
                data, RIVERCROSS, "Rivercross", residents, 40L, false);

        assertTrue(result.available());
        assertEquals(null, result.government());
        assertEquals(null, data.polity(result.polity()).governmentOrganization());
        assertEquals(2, result.residencesCreated());
        assertTrue(data.organizations().isEmpty());
        assertTrue(data.memberships().isEmpty());
    }

    @Test
    void recognizingANewHomeEndsTheOldResidenceWithoutTouchingMemberships() {
        PoliticalSavedData data = new PoliticalSavedData();
        UUID resident = UUID.randomUUID();
        PoliticalVillageBootstrap.Result oldHome = PoliticalVillageBootstrap.ensure(
                data, RIVERCROSS, "Rivercross", List.of(resident), 40L);
        SettlementRef hilltop = new SettlementRef(id("minecraft:overworld"), 8);
        PoliticalVillageBootstrap.Result newHome = PoliticalVillageBootstrap.ensure(
                data, hilltop, "Hilltop", List.of(resident), 90L);

        List<AffiliationInstance> residences = data.affiliations(resident).stream()
                .filter(value -> value.kind().equals(RESIDENCE)).toList();
        assertEquals(2, residences.size());
        assertEquals(1, residences.stream().filter(AffiliationInstance::active).count());
        assertTrue(residences.stream().anyMatch(value -> value.active()
                && value.actor().id().equals(newHome.polity())));
        assertTrue(residences.stream().anyMatch(value -> !value.active()
                && value.actor().id().equals(oldHome.polity()) && value.endedAt() == 90L));
        assertEquals(2, data.memberships(resident).size(), "civic residence never erases organization history");
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }
}
