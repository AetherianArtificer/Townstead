package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DamageTriggerTypeTest {
    @Test
    void parsesPerspectiveThresholdAndSources() {
        DamageTriggerType type = new DamageTriggerType();
        var parsed = (DamageTriggerType.Instance) type.parse(JsonParser.parseString(
                "{\"role\":\"WITNESS\",\"min_amount\":3.5,\"sources\":[\"Mob\",\"Arrow\"]}")
                .getAsJsonObject());

        assertEquals("witness", parsed.role());
        assertEquals(3.5f, parsed.minAmount());
        assertEquals(java.util.List.of("mob", "arrow"), parsed.sources());

        var id = ResourceLocation.tryParse("example:witnessed_harm");
        TriggerIndex.Builder builder = TriggerIndex.builder();
        type.index(parsed, id, builder);
        assertEquals(java.util.List.of(id), builder.build().matchesFor("damage", "witness"));
    }

    @Test
    void rejectsUnknownRoleAndNegativeThreshold() {
        DamageTriggerType type = new DamageTriggerType();
        assertNull(type.parse(JsonParser.parseString("{\"role\":\"bystander\"}").getAsJsonObject()));
        assertNull(type.parse(JsonParser.parseString("{\"min_amount\":-1}").getAsJsonObject()));
    }
}
