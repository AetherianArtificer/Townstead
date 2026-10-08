package com.aetherianartificer.townstead.reaction.trigger.types;

import com.aetherianartificer.townstead.reaction.TriggerIndex;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ContextExitTriggerTypeTest {
    @Test
    void normalizesAndIndexesEachRequiredTag() {
        ContextExitTriggerType type = new ContextExitTriggerType();
        var instance = type.parse(JsonParser.parseString(
                "{\"tags\":[\"Raid_Active\",\"Under_Roof\"]}").getAsJsonObject());
        var id = ResourceLocation.tryParse("example:all_clear");
        TriggerIndex.Builder builder = TriggerIndex.builder();

        type.index(instance, id, builder);
        TriggerIndex index = builder.build();

        assertEquals(java.util.List.of(id), index.matchesFor("context_exit", "raid_active"));
        assertEquals(java.util.List.of(id), index.matchesFor("context_exit", "under_roof"));
    }

    @Test
    void requiresAtLeastOneTag() {
        ContextExitTriggerType type = new ContextExitTriggerType();
        assertNull(type.parse(JsonParser.parseString("{}").getAsJsonObject()));
    }
}
