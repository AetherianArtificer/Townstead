package com.aetherianartificer.townstead.pheno.state;

import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.types.ChangeStateActionType;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.types.EntityTypeConditionType;
import com.aetherianartificer.townstead.pheno.condition.types.LogicConditionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoredVampireStateTest {
    private static final List<String> EFFECTS = List.of("vampire_turning_look", "vampire_fledgling_look",
            "vampire_look", "vampire_features", "vampire_elder_look", "vampire_fledgling_nature", "vampire_thirst",
            "vampire_nature", "vampire_cold_body", "vampire_elder_nature");
    private static final List<String> LEVEL_UPS = List.of("vampire_level_2", "vampire_level_3", "vampire_level_4");
    private static final List<String> BACKINGS = List.of("vampire_villager", "vampire_sanguinare",
            "vampire_player_level", "vampire_player_lord", "vampire_blood_villager", "vampire_player_age");

    @BeforeAll
    static void registerVocabulary() {
        ConditionTypes.register(new EntityTypeConditionType("pheno:entity_type"));
        ConditionTypes.register(new LogicConditionType("pheno:or", LogicConditionType.Mode.OR));
        ConditionTypes.register(new LogicConditionType("pheno:not", LogicConditionType.Mode.NOT));
        ConditionTypes.register(new LogicConditionType("pheno:and", LogicConditionType.Mode.AND));
        ConditionTypes.register(new com.aetherianartificer.townstead.pheno.condition.types.EntityStateConditionType());
        ConditionTypes.register(new com.aetherianartificer.townstead.root.condition.types.RigConditionType());
        ActionTypes.register(new com.aetherianartificer.townstead.pheno.action.types.AndActionType());
        ActionTypes.register(new ChangeStateActionType("pheno:add_state", ChangeStateActionType.Mode.ADD));
    }

    @Test
    void everyAuthoredPieceParsesAndNamesARealTier() throws Exception {
        EntityStateDefinition vampire = EntityStateDefinition.parse(id("townstead_state:vampire"),
                read("/data/townstead_state/entity_state/vampire.json"));
        assertEquals(EntityStateDefinition.DeathPolicy.KEEP, vampire.deathPolicy());
        assertNotNull(vampire.aspect());

        for (String name : EFFECTS) {
            JsonObject json = read("/data/townstead/state_effect/" + name + ".json");
            assertEquals("vampirism", json.get("mods").getAsString(), name);
            StateEffect effect = StateEffect.parse(id("townstead:" + name), json);
            String tier = effect.tier() != null ? effect.tier() : effect.minTier();
            assertNotNull(vampire.tier(tier), name + " names tier " + tier);
        }
        for (String name : BACKINGS) {
            JsonObject json = read("/data/townstead/state_backing/" + name + ".json");
            assertTrue(json.get("mods").toString().contains("\"vampirism\""), name);
            StateBacking backing = StateBacking.parse(id("townstead:" + name), json);
            for (StateBacking.Level level : backing.amplifierLevels().values()) {
                if (level.tier() != null) assertNotNull(vampire.tier(level.tier()), name + " maps to tier " + level.tier());
                else assertTrue(level.amount() <= vampire.max(), name + " maps inside the level scale");
            }
        }
        for (String name : LEVEL_UPS) {
            StateEffect effect = StateEffect.parse(id("townstead:" + name),
                    read("/data/townstead/state_effect/" + name + ".json"));
            assertEquals(id("townstead_state:vampire_blood"), effect.state());
            assertNotNull(effect.whileActive(), name);
        }
    }

    @Test
    void levelsSitOnVampirismsOwnScale() throws Exception {
        EntityStateDefinition vampire = EntityStateDefinition.parse(id("townstead_state:vampire"),
                read("/data/townstead_state/entity_state/vampire.json"));
        assertEquals("fledgling", vampire.tier(1).id());
        assertEquals("fledgling", vampire.tier(3).id());
        assertEquals("vampire", vampire.tier(4).id());
        assertEquals("vampire", vampire.tier(14).id());
        assertEquals("elder", vampire.tier(15).id());
    }

    @Test
    void mechanicsSkipPlayersBecauseVampirismRunsTheirs() throws Exception {
        for (String name : List.of("vampire_fledgling_nature", "vampire_thirst", "vampire_nature",
                "vampire_features", "vampire_elder_look")) {
            StateEffect effect = StateEffect.parse(id("townstead:" + name),
                    read("/data/townstead/state_effect/" + name + ".json"));
            assertNotNull(effect.condition(), name);
        }
    }

    @Test
    void laterTiersOutrankEarlierLooks() throws Exception {
        int previous = Integer.MIN_VALUE;
        for (String name : List.of("vampire_turning_look", "vampire_fledgling_look", "vampire_look")) {
            int priority = StateEffect.parse(id("townstead:" + name),
                    read("/data/townstead/state_effect/" + name + ".json")).priority();
            assertTrue(priority > previous, name);
            previous = priority;
        }
    }

    private static JsonObject read(String path) throws Exception {
        try (var stream = AuthoredVampireStateTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    private static ResourceLocation id(String raw) { return ResourceLocation.tryParse(raw); }
}
