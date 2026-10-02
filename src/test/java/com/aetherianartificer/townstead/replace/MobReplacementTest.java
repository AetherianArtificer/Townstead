package com.aetherianartificer.townstead.replace;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobReplacementTest {
    private static final ResourceLocation VAMPIRE = ResourceLocation.tryParse("townstead_state:vampire");

    private static MobReplacement bundled(String name) throws Exception {
        String path = "/data/townstead/mob_replacement/" + name + ".json";
        try (var stream = MobReplacementTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return MobReplacement.parse(ResourceLocation.tryParse("townstead:" + name),
                    JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
        }
    }

    @Test
    void vampiresArriveAsRealVampiresAndStandInForTheMob() throws Exception {
        MobReplacement vampire = bundled("vampirism_vampire");
        assertTrue(vampire.types().contains(ResourceLocation.tryParse("vampirism:vampire")));
        assertArrayEquals(new double[]{4, 8}, vampire.states().get(VAMPIRE));
        assertTrue(vampire.creditKills());
        assertTrue(vampire.replacedLoot());
        assertEquals("vampirism.replaceSpawns", vampire.setting());
        assertTrue(vampire.spawnTypes().contains("natural"));

        MobReplacement advanced = bundled("vampirism_advanced_vampire");
        assertArrayEquals(new double[]{15, 19}, advanced.states().get(VAMPIRE));
    }

    @Test
    void rejectsMalformedReplacements() {
        assertThrows(IllegalArgumentException.class, () -> MobReplacement.parse(ResourceLocation.tryParse("t:x"),
                JsonParser.parseString("{\"schema\":\"townstead:mob_replacement/v1\",\"replaces\":[]}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> MobReplacement.parse(ResourceLocation.tryParse("t:x"),
                JsonParser.parseString("{\"schema\":\"townstead:mob_replacement/v1\",\"replaces\":[\"a:b\"],\"chance\":2}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> MobReplacement.parse(ResourceLocation.tryParse("t:x"),
                JsonParser.parseString("{\"schema\":\"townstead:mob_replacement/v1\",\"replaces\":[\"a:b\"],\"states\":{\"a:s\":[5,1]}}").getAsJsonObject()));
    }
}
