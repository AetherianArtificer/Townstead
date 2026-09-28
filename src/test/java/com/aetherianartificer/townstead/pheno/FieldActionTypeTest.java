package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.pheno.action.types.LeapActionType;
import com.aetherianartificer.townstead.pheno.action.types.RetrieveItemActionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FieldActionTypeTest {
    private JsonObject json(String s) { return JsonParser.parseString(s.replace('\'', '"')).getAsJsonObject(); }
    @Test void boundedLeapParameters() {
        var type = new LeapActionType();
        assertNotNull(type.parse(json("{}")));
        assertNotNull(type.parse(json("{'upward_velocity':0.7,'forward_velocity':0.18}")));
        assertNull(type.parse(json("{'upward_velocity':100}")));
        assertNull(type.parse(json("{'upward_velocity':1e999}")));
        assertNull(type.parse(json("{'forward_velocity':-1}")));
    }
    @Test void boundedRetrievalParameters() {
        var type = new RetrieveItemActionType();
        assertNotNull(type.parse(json("{}")));
        assertNull(type.parse(json("{'range':100}")));
        assertNull(type.parse(json("{'range':1e999}")));
        assertNull(type.parse(json("{'speed':0}")));
        assertNull(type.parse(json("{'aim_radius':10}")));
        assertNull(type.parse(json("{'extend_ticks':0}")));
        assertNull(type.parse(json("{'color':'pink'}")));
    }
}
