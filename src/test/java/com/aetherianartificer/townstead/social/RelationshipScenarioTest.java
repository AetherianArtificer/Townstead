package com.aetherianartificer.townstead.social;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import com.aetherianartificer.townstead.pheno.condition.*;
import com.aetherianartificer.townstead.pheno.condition.types.*;
import com.aetherianartificer.townstead.pheno.value.ValueTypes;
import com.aetherianartificer.townstead.pheno.value.types.RelationshipValueType;
import com.aetherianartificer.townstead.root.CanonicalStage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RelationshipScenarioTest {
    @BeforeAll static void loadDefinitions() throws Exception {
        ValueTypes.register(new RelationshipValueType());
        ConditionTypes.register(new ValueConditionType());
        ConditionTypes.register(new LogicConditionType("pheno:and", LogicConditionType.Mode.AND));
        Path root = resources().resolve("data/townstead/relationship_quality");
        Map<ResourceLocation, RelationshipQuality> definitions = new LinkedHashMap<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                ResourceLocation id = ResourceLocation.tryParse("townstead:" + name);
                definitions.put(id, RelationshipQuality.parse(id,
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject(), Map.of()));
            }
        }
        RelationshipQualities.replaceAll(definitions);
        assertTrue(definitions.size() >= 8);
    }

    @Test void bundledDescriptorsComposeSharedPhenoAndCanOverlap() throws Exception {
        Path root = resources().resolve("data/townstead/relationship_descriptor");
        List<RelationshipDescriptor> descriptors = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                descriptors.add(RelationshipDescriptor.parse(ResourceLocation.tryParse("townstead:" + name),
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject(), Map.of()));
            }
        }
        RelationshipDescriptors.replaceAll(descriptors);
        UUID self = UUID.randomUUID(), other = UUID.randomUUID();
        SocialKnowledge knowledge = qualities(other, Map.of(
                "townstead:affection", -25D, "townstead:respect", 55D,
                "townstead:trust", 0D, "townstead:resentment", 20D, "townstead:comfort", 0D));
        List<String> matches = RelationshipDescriptors.matching(new ConditionContext(subject(self, knowledge), other))
                .stream().map(value -> value.id().toString()).toList();
        assertTrue(matches.contains("townstead:admired_rival"), matches.toString());
        assertFalse(matches.contains("townstead:trusted_friend"), matches.toString());
    }

    @Test void bundledAsymmetricScenariosPassThroughProductionLedger() throws Exception {
        Path root = resources().resolve("data/townstead/relationship_scenario");
        int count = 0;
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                RelationshipScenario scenario = RelationshipScenario.parse(
                        JsonParser.parseString(Files.readString(file)).getAsJsonObject());
                RelationshipScenario.Result result = scenario.run();
                assertTrue(result.passed(), scenario.id() + "\n" + String.join("\n", result.failures())
                        + "\n" + String.join("\n", result.trace()));
                count++;
            }
        }
        assertTrue(count >= 2);
    }

    @Test void authorErrorsIncludeTheOffendingField() {
        var json = JsonParser.parseString("""
                {"schema":"townstead:relationship_scenario/v1","id":"test:bad","people":["a","b"],
                 "changes":[],"checks":[{"from":"a","toward":"b","quality":"bad id","day":0}]}
                """).getAsJsonObject();
        assertTrue(assertThrows(IllegalArgumentException.class, () -> RelationshipScenario.parse(json))
                .getMessage().contains("quality"));
    }

    private static Path resources() throws Exception {
        return Path.of(Objects.requireNonNull(RelationshipScenarioTest.class.getClassLoader()
                .getResource("data/townstead/relationship_quality")).toURI()).getParent().getParent().getParent();
    }
    private static SocialKnowledge qualities(UUID other, Map<String, Double> values) {
        return new SocialKnowledge() {
            public long today(){return 0;} public List<Memory> memories(){return List.of();}
            public double sentiment(UUID toward){return values.getOrDefault("townstead:affection",0D);}
            public double relationship(UUID toward,String quality){return toward.equals(other)?values.getOrDefault(quality,0D):0D;}
        };
    }
    private static PhenoSubject subject(UUID id, SocialKnowledge knowledge) {
        return new PhenoSubject() {
            public UUID uuid(){return id;} public String displayName(){return "Test";}
            public String professionId(){return "";} public CanonicalStage lifeStage(){return CanonicalStage.ADULT;}
            public Bonds bonds(){return Bonds.EMPTY;} public int counter(String key){return 0;}
            public SocialKnowledge socialKnowledge(){return knowledge;}
        };
    }
}
