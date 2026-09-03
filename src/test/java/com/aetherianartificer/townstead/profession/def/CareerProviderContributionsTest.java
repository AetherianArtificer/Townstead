package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CareerProviderContributionsTest {

    private static final ResourceLocation BEVERAGE = id("townstead:beverage_artisan");

    @Test
    void providersMergeByPriorityThenResourceIdWithStableUnions() {
        Map<ResourceLocation, JsonObject> documents = new LinkedHashMap<>();
        documents.put(id("zpack:late"), provider(20, "#222222",
                "herbalbrews:tea_kettle", "minecraft:barrel"));
        documents.put(id("apack:first"), provider(10, "#111111",
                "farmersdelight:cooking_pot", "minecraft:barrel"));
        Map<ResourceLocation, String> errors = new LinkedHashMap<>();
        var plan = CareerProviderContributions.plan(documents,
                Map.of(BEVERAGE, object("{}")), errors);
        JsonObject path = object("{\"skills\":[\"gateway\"],\"worksites\":[\"minecraft:barrel\"]}");

        plan.applyPath(BEVERAGE, "barista", path);

        assertTrue(errors.isEmpty());
        assertEquals("#222222", path.get("color").getAsString(),
                "higher priority is applied later for explicit presentation conflicts");
        assertEquals(List.of("minecraft:barrel", "farmersdelight:cooking_pot",
                        "herbalbrews:tea_kettle"),
                path.getAsJsonArray("worksites").asList().stream()
                        .map(element -> element.getAsString()).toList());
        assertEquals(List.of(id("apack:first"), id("zpack:late")),
                plan.provenance().get(new CareerProviderContributions.Target(BEVERAGE, "barista")));
    }

    @Test
    void professionWorkComposesWithoutReplacingBaseAndReloadPlanStartsClean() {
        JsonObject provider = object("""
                {"schema":"townstead:career_provider/v1",
                 "profession":"townstead:beverage_artisan","path":"brewer",
                 "contributes":{"profession":{
                   "poi":[{"type":"townstead:station_post","blocks":["brewery:wooden_brewingstation"]}],
                   "tasks":[{"type":"townstead_work:brew","weight":10}]}}}
                """);
        Map<ResourceLocation, JsonObject> professions = new LinkedHashMap<>();
        professions.put(BEVERAGE, object("""
                {"poi":[{"type":"townstead:building","type_prefix":"brewery_l"}],
                 "work_tasks":[{"type":"townstead_work:deliver","weight":5}]}
                """));
        var plan = CareerProviderContributions.plan(Map.of(id("townstead:brewery"), provider),
                professions, new LinkedHashMap<>());

        plan.applyProfessions(professions);

        assertEquals(2, professions.get(BEVERAGE).getAsJsonArray("poi").size());
        assertEquals(2, professions.get(BEVERAGE).getAsJsonArray("work_tasks").size());
        var emptyReload = CareerProviderContributions.plan(Map.of(),
                Map.of(BEVERAGE, object("{}")), new LinkedHashMap<>());
        assertFalse(emptyReload.hasProvider(BEVERAGE, "brewer"));
        assertTrue(emptyReload.provenance().isEmpty());
    }

    @Test
    void foreignProfessionAliasesRetainNativeIdentityAndResolveToAPath() {
        JsonObject provider = object("""
                {"schema":"townstead:career_provider/v1",
                 "profession":"townstead:beverage_artisan","path":"bartender",
                 "aliases":["beachparty:sandymerchant"],"contributes":{"path":{}}}
                """);
        var plan = CareerProviderContributions.plan(Map.of(id("townstead:beachparty"), provider),
                Map.of(BEVERAGE, object("{}")), new LinkedHashMap<>());
        JsonObject composed = object("{\"paths\":[{\"id\":\"bartender\"}]}");

        Map<ResourceLocation, ProfessionDefs.Resolution> aliases = plan.aliases(
                Set.of(BEVERAGE), Map.of(BEVERAGE, composed));

        assertEquals(new ProfessionDefs.Resolution(BEVERAGE, "bartender"),
                aliases.get(id("beachparty:sandymerchant")));
    }

    @Test
    void malformedProviderFailsIndependently() {
        Map<ResourceLocation, String> errors = new LinkedHashMap<>();
        var plan = CareerProviderContributions.plan(Map.of(id("bad:provider"), object("""
                {"schema":"townstead:career_provider/v1",
                 "profession":"townstead:beverage_artisan","path":"barista",
                 "contributes":{"path":{"mystery_knob":true}}}
                """)), Map.of(BEVERAGE, object("{}")), errors);

        assertTrue(plan.provenance().isEmpty());
        assertTrue(errors.get(id("bad:provider")).contains("mystery_knob"));
    }

    private static JsonObject provider(int priority, String color, String worksite, String duplicate) {
        return object("""
                {"schema":"townstead:career_provider/v1",
                 "profession":"townstead:beverage_artisan","path":"barista",
                 "priority":%d,
                 "contributes":{"path":{"color":"%s","worksites":["%s","%s"]}}}
                """.formatted(priority, color, worksite, duplicate));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static ResourceLocation id(String id) {
        return ResourceLocation.tryParse(id);
    }
}
