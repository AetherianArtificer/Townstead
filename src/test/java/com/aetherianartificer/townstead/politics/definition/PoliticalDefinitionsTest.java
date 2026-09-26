package com.aetherianartificer.townstead.politics.definition;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.ConstantConditionType;
import com.aetherianartificer.townstead.politics.founding.FoundingProfileDefinition;
import com.aetherianartificer.townstead.politics.founding.FoundingProfiles;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoliticalDefinitionsTest {
    @BeforeAll
    static void registerPhenoVocabulary() {
        ConditionTypes.register(new ConstantConditionType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.StandingValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.VillageNeedsValueType());
        com.aetherianartificer.townstead.pheno.value.ValueTypes.register(
                new com.aetherianartificer.townstead.pheno.value.types.VillageSpiritTierValueType());
    }

    @AfterEach
    void clearDefinitions() {
        PoliticalDefinitions.replace(Map.of(), Map.of(), Map.of());
    }

    @Test
    void bundledPoliticalDefinitionsFormOneCoherentSnapshot() throws Exception {
        Map<ResourceLocation, OrganizationRoleDefinition> roles = parseDirectory(
                "organization_role", (id, json) -> OrganizationRoleDefinition.parse(id, json, Map.of()));
        Map<ResourceLocation, MembershipPolicyDefinition> policies = parseDirectory(
                "membership_policy", MembershipPolicyDefinition::parse);
        Map<ResourceLocation, OrganizationKindDefinition> kinds = parseDirectory(
                "organization_kind", (id, json) -> OrganizationKindDefinition.parse(id, json, Map.of()));

        assertEquals(6, roles.size());
        assertEquals(4, policies.size());
        assertEquals(4, kinds.size());
        assertTrue(PoliticalDefinitions.validate(roles, policies, kinds).isEmpty(),
                () -> String.join("\n", PoliticalDefinitions.validate(roles, policies, kinds)));

        OrganizationKindDefinition civic = kinds.get(id("townstead:civic_faction"));
        assertTrue(civic.tags().contains(id("townstead:political")));
        assertTrue(civic.roles().stream().anyMatch(binding -> binding.founder()
                && binding.role().equals(id("townstead:founder"))));

        OrganizationKindDefinition guild = kinds.get(id("townstead:guild"));
        assertTrue(guild.tags().contains(id("townstead:nonexclusive")));

        OrganizationKindDefinition playerFaction = kinds.get(id("townstead:player_faction"));
        assertEquals(id("townstead:player_faction"), playerFaction.membershipPolicy());
        assertTrue(playerFaction.roles().stream().anyMatch(binding -> binding.founder()
                && binding.role().equals(id("townstead:faction_leader"))
                && binding.minimum() == 1 && binding.maximum() == 1));
        MembershipPolicyDefinition playerPolicy = policies.get(playerFaction.membershipPolicy());
        assertEquals(id("townstead:faction_leader"), playerPolicy.admission().decision().role());
        assertEquals(java.util.List.of(id("townstead:member")), playerPolicy.admission().initialRoles());
        assertEquals(id("townstead:faction_leader"), playerFaction.governance().head());
        assertEquals(GovernanceRoutes.FAVOR, playerFaction.governance().succession());

        GovernanceDefinition council = kinds.get(id("townstead:village_council")).governance();
        assertEquals(id("townstead:presiding_councilor"), council.head());
        assertEquals(GovernanceRoutes.COUNCIL_VOTE, council.succession());
        assertEquals(50, council.legitimacy().base());
        assertEquals(2, council.legitimacy().sources().size());
        assertEquals(2, council.routes().size());

        PoliticalDefinitions.replace(roles, policies, kinds);
        Path profileFile = Path.of(Objects.requireNonNull(PoliticalDefinitionsTest.class.getClassLoader()
                .getResource("data/townstead/founding_profile/default_village.json")).toURI());
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("townstead:default_village"),
                JsonParser.parseString(Files.readString(profileFile)).getAsJsonObject(), Map.of());
        assertTrue(FoundingProfiles.validate(profile).isEmpty(),
                () -> String.join("\n", FoundingProfiles.validate(profile)));
        assertEquals(id("townstead:village_council"), profile.government().organizationKind());
        assertEquals(3, profile.government().seats().stream().mapToInt(FoundingProfileDefinition.Seat::count).sum());
        assertEquals(id("townstead:cultural_affinity"), profile.population().strategy());
        assertTrue(profile.population().adjustments().isEmpty(), "the starter population is an open strategy, not an allowlist");
    }

    @Test
    void noGovernmentProfileIsAValidFirstClassDefinition() {
        FoundingProfileDefinition profile = FoundingProfileDefinition.parse(id("townstead:no_formal_government"),
                JsonParser.parseString("""
                        {
                          "schema":"townstead:founding_profile/v1",
                          "name":"No Formal Government",
                          "weight":0
                        }
                        """).getAsJsonObject(), Map.of());

        assertEquals(null, profile.government());
        assertTrue(FoundingProfiles.validate(profile).isEmpty());
    }

    @Test
    void unknownPhenoEligibilityFailsClosed() {
        JsonObject json = JsonParser.parseString("""
                {
                  "schema":"townstead:membership_policy/v1",
                  "admission":{
                    "procedure":"townstead:open_admission",
                    "eligibility":{"type":"example:not_registered"}
                  },
                  "departure":{"procedure":"townstead:free_resignation"}
                }
                """).getAsJsonObject();

        assertThrows(IllegalArgumentException.class,
                () -> MembershipPolicyDefinition.parse(id("test:closed"), json));
    }

    @Test
    void governanceNamesOnlyImplementedRoutes() {
        JsonObject json = governanceKind("""
                {"head":"townstead:member","succession":"test:trial_by_lottery"}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> OrganizationKindDefinition.parse(id("test:lottery"), json, Map.of()));
    }

    @Test
    void governanceOfficesMustBeRolesOfTheKind() {
        JsonObject json = governanceKind("""
                {"head":"test:emperor","succession":"townstead:favor"}
                """);

        assertThrows(IllegalArgumentException.class,
                () -> OrganizationKindDefinition.parse(id("test:empire"), json, Map.of()));
    }

    private static JsonObject governanceKind(String governance) {
        JsonObject json = JsonParser.parseString("""
                {
                  "schema":"townstead:organization_kind/v1",
                  "membership_policy":"townstead:open",
                  "roles":[{"role":"townstead:member","min":1,"max":1}],
                  "founding":{"procedure":"townstead:charter"}
                }
                """).getAsJsonObject();
        json.add("governance", JsonParser.parseString(governance));
        return json;
    }

    @Test
    void malformedRoleCardinalityIsRejected() {
        JsonObject json = JsonParser.parseString("""
                {
                  "schema":"townstead:organization_kind/v1",
                  "membership_policy":"townstead:open",
                  "roles":[{"role":"townstead:member","min":2,"max":1}],
                  "founding":{"procedure":"townstead:charter"}
                }
                """).getAsJsonObject();

        assertThrows(IllegalArgumentException.class,
                () -> OrganizationKindDefinition.parse(id("test:broken"), json, Map.of()));
    }

    @Test
    void unresolvedReferencesAreReportedWithTheirOwner() {
        MembershipPolicyDefinition policy = MembershipPolicyDefinition.parse(id("test:policy"),
                JsonParser.parseString("""
                        {
                          "schema":"townstead:membership_policy/v1",
                          "admission":{
                            "procedure":"townstead:open_admission",
                            "initial_roles":["test:missing"]
                          },
                          "departure":{"procedure":"townstead:free_resignation"}
                        }
                        """).getAsJsonObject());

        var errors = PoliticalDefinitions.validate(Map.of(), Map.of(policy.id(), policy), Map.of());
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("test:policy"));
        assertTrue(errors.get(0).contains("test:missing"));
    }

    private static <T> Map<ResourceLocation, T> parseDirectory(String directory, Parser<T> parser)
            throws Exception {
        Path root = Path.of(Objects.requireNonNull(PoliticalDefinitionsTest.class.getClassLoader()
                .getResource("data/townstead/" + directory)).toURI());
        Map<ResourceLocation, T> out = new LinkedHashMap<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString();
                ResourceLocation id = id("townstead:" + name.substring(0, name.length() - 5));
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                out.put(id, parser.parse(id, json));
            }
        }
        return out;
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = DataPackLang.parseId(value);
        if (parsed == null) throw new IllegalArgumentException(value);
        return parsed;
    }

    @FunctionalInterface
    private interface Parser<T> {
        T parse(ResourceLocation id, JsonObject json);
    }
}
