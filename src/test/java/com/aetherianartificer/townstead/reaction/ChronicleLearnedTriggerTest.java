package com.aetherianartificer.townstead.reaction;

import com.aetherianartificer.townstead.reaction.trigger.types.ChronicleLearnedTriggerType;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChronicleLearnedTriggerTest {
    @Test void indexesSpecificTemplatesAndFiltersChannelAndConfidence() {
        var parser=new ChronicleLearnedTriggerType();
        var instance=(ChronicleLearnedTriggerType.Instance)parser.parse(JsonParser.parseString("""
            {"template":"test:help","channel":"witness","min_fidelity":0.7}
            """).getAsJsonObject());
        var builder=TriggerIndex.builder(); var id=ResourceLocation.tryParse("test:reaction"); parser.index(instance,id,builder);
        assertEquals(java.util.List.of(id),builder.build().matchesFor("chronicle_learned","test:help"));
        assertTrue(instance.matches("test:help","witness",1));
        assertFalse(instance.matches("test:help","gossip",1));
        assertFalse(instance.matches("test:help","witness",0.5));
        assertFalse(instance.matches("test:other","witness",1));
        assertNull(parser.parse(JsonParser.parseString("{\"min_fidelity\":2}").getAsJsonObject()));
        assertNull(parser.parse(JsonParser.parseString("{\"tempalte\":\"test:help\"}").getAsJsonObject()));
    }
}
