package com.aetherianartificer.townstead.work.feedback;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BundledProfessionFeedbackTest {
    private static final List<String> SETTINGS = List.of(
            "/data/minecraft/profession/butcher/feedback.json",
            "/data/minecraft/profession/cleric/feedback.json",
            "/data/minecraft/profession/farmer/feedback.json",
            "/data/minecraft/profession/fisherman/feedback.json",
            "/data/minecraft/profession/leatherworker/feedback.json",
            "/data/townstead/profession/beverage_artisan/feedback.json",
            "/data/townstead/profession/cook/feedback.json");
    private static final List<String> RULES = List.of(
            "/data/minecraft/profession/butcher/feedback/no_worksite.json",
            "/data/minecraft/profession/butcher/feedback/no_blood_grate.json",
            "/data/minecraft/profession/butcher/feedback/no_cleaver.json",
            "/data/minecraft/profession/butcher/feedback/no_grinder.json",
            "/data/minecraft/profession/butcher/feedback/no_hook.json",
            "/data/minecraft/profession/butcher/feedback/no_livestock.json",
            "/data/minecraft/profession/butcher/feedback/no_skin_rack.json",
            "/data/minecraft/profession/butcher/feedback/no_skinning_knife.json",
            "/data/minecraft/profession/butcher/feedback/shop_promoted_to_tier_1.json",
            "/data/minecraft/profession/butcher/feedback/shop_promoted_to_tier_2.json",
            "/data/minecraft/profession/butcher/feedback/shop_promoted_to_tier_3.json",
            "/data/minecraft/profession/butcher/feedback/slaughter_disabled.json",
            "/data/minecraft/profession/cleric/feedback/no_worksite.json",
            "/data/minecraft/profession/cleric/feedback/no_input.json",
            "/data/minecraft/profession/cleric/feedback/no_fuel.json",
            "/data/minecraft/profession/cleric/feedback/no_storage.json",
            "/data/minecraft/profession/cleric/feedback/unreachable.json",
            "/data/minecraft/profession/farmer/feedback/no_seeds.json",
            "/data/minecraft/profession/fisherman/feedback/no_rod.json",
            "/data/minecraft/profession/leatherworker/feedback/no_hide.json",
            "/data/townstead/profession/beverage_artisan/feedback/no_worksite.json",
            "/data/townstead/profession/cook/feedback/no_worksite.json");

    @BeforeAll
    static void registerConditionsUsedByBundledRules() {
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.work.condition.WorksiteConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.InventoryConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.ConfigConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new WorkSignalConditionType());
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType("pheno:entity_type"));
        for (String key : List.of("pheno:alive", "pheno:baby", "pheno:tamed", "pheno:named")) {
            com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                    new com.aetherianartificer.townstead.pheno.condition.types.StateConditionType(
                            key, ignored -> true));
        }
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:and",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.AND));
        com.aetherianartificer.townstead.pheno.condition.ConditionTypes.register(
                new com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType(
                        "pheno:or",
                        com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType.Mode.OR));
    }

    @Test
    void bundledSidecarsUseTheirSingularSchemas() {
        SETTINGS.forEach(path -> assertSchema(path, ProfessionFeedbackDocument.SETTINGS_SCHEMA));
        RULES.forEach(path -> assertSchema(path, ProfessionFeedbackDocument.RULE_SCHEMA));
    }

    @Test
    void bundledRuleConditionsCompile() {
        for (String path : RULES) {
            JsonObject json = read(path);
            ProfessionFeedbackDocument.Rule rule = ProfessionFeedbackDocument.Rule.parse(
                    id("test:" + path.substring(path.lastIndexOf('/') + 1, path.length() - 5)),
                    id("minecraft:butcher"), "rule", json);
            assertNotNull(rule.when(), path);
        }
    }

    private static void assertSchema(String path, String expected) {
        JsonObject json = read(path);
        assertEquals(expected, json.get("schema").getAsString(), path);
    }

    private static JsonObject read(String path) {
        InputStream input = BundledProfessionFeedbackTest.class.getResourceAsStream(path);
        assertNotNull(input, "shipped resource missing: " + path);
        return JsonParser.parseReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static net.minecraft.resources.ResourceLocation id(String value) {
        //? if >=1.21 {
        return net.minecraft.resources.ResourceLocation.parse(value);
        //?} else {
        /*return new net.minecraft.resources.ResourceLocation(value);
        *///?}
    }
}
