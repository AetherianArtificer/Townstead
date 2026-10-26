package com.aetherianartificer.townstead.profession.def;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfessionTradeDocumentTest {

    @Test
    void distinctRootContributionsMergeByMerchantLevel() {
        JsonObject profession = object("{}");
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1",
                 "1":[{"cost":"minecraft:emerald","result":"minecraft:honeycomb"}]}
                """));
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1",
                 "1":[{"cost":"minecraft:emerald","result":"minecraft:candle"}]}
                """));

        assertEquals(2, profession.getAsJsonObject("trades").getAsJsonArray("1").size());
        assertFalse(profession.getAsJsonObject("trades").getAsJsonArray("1")
                .get(0).getAsJsonObject().has("path"));
    }

    @Test
    void modsIsDocumentMetadataRatherThanAMerchantLevel() {
        JsonObject profession = object("{}");
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1","mods":"butchery",
                 "2":[{"cost":"minecraft:emerald","result":"butchery:raw_sausage"}]}
                """));

        assertEquals(1, profession.getAsJsonObject("trades").getAsJsonArray("2").size());
    }

    @Test
    void pathContributionLinksEveryOfferToItsOwner() {
        JsonObject profession = object("{\"paths\":[{\"id\":\"hive_keeper\"}]}");
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1",
                 "2":[{"cost":"minecraft:emerald","result":"minecraft:beehive"}],
                 "3":[{"cost":"minecraft:emerald","result":"minecraft:campfire"}]}
                """), "hive_keeper");

        assertEquals("hive_keeper", profession.getAsJsonObject("trades").getAsJsonArray("2")
                .get(0).getAsJsonObject().get("path").getAsString());
        assertEquals("hive_keeper", profession.getAsJsonObject("trades").getAsJsonArray("3")
                .get(0).getAsJsonObject().get("path").getAsString());
    }

    @Test
    void replaceClearsOnlyTheContributionTarget() {
        JsonObject profession = object("{\"paths\":[{\"id\":\"hive_keeper\"}]}");
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1",
                 "1":[{"cost":"minecraft:emerald","result":"minecraft:honeycomb"}]}
                """));
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1",
                 "2":[{"cost":"minecraft:emerald","result":"minecraft:beehive"}]}
                """), "hive_keeper");
        ProfessionTradeDocument.apply(profession, object("""
                {"schema":"townstead:profession_trade/v1","replace":true,
                 "4":[{"cost":"minecraft:emerald","result":"minecraft:campfire"}]}
                """), "hive_keeper");

        assertEquals(1, profession.getAsJsonObject("trades").getAsJsonArray("1").size(),
                "root Profession offers survive a Path replacement");
        assertEquals(0, profession.getAsJsonObject("trades").getAsJsonArray("2").size());
        assertEquals("minecraft:campfire", profession.getAsJsonObject("trades").getAsJsonArray("4")
                .get(0).getAsJsonObject().get("result").getAsString());
    }

    @Test
    void schemaLevelsAndPathsAreEnforced() {
        assertThrows(IllegalArgumentException.class, () -> ProfessionTradeDocument.apply(
                object("{}"), object("{\"1\":[]}")));
        assertThrows(IllegalArgumentException.class, () -> ProfessionTradeDocument.apply(
                object("{}"), object("""
                        {"schema":"townstead:profession_trade/v1","6":[]}
                        """)));
        assertThrows(IllegalArgumentException.class, () -> ProfessionTradeDocument.apply(
                object("{\"paths\":[{\"id\":\"hive_keeper\"}]}"),
                object("{\"schema\":\"townstead:profession_trade/v1\",\"2\":[]}"),
                "field_keeper"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionTradeDocument.apply(
                object("{\"paths\":[{\"id\":\"hive_keeper\"}]}"), object("""
                        {"schema":"townstead:profession_trade/v1",
                         "2":[{"cost":"minecraft:emerald","result":"minecraft:beehive",
                               "path":"field_keeper"}]}
                        """), "hive_keeper"));
        assertThrows(IllegalArgumentException.class, () -> ProfessionTradeDocument.apply(
                object("{\"paths\":[{\"id\":\"hive_keeper\"}]}"), object("""
                        {"schema":"townstead:profession_trade/v1","path":"hive_keeper","2":[]}
                        """), "hive_keeper"));
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
