package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.pheno.action.types.TeleportActionType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TeleportActionTypeTest {

    private static JsonObject obj(String singleQuoted) {
        return JsonParser.parseString(singleQuoted.replace('\'', '"')).getAsJsonObject();
    }

    @Test
    void acceptsDirectionalDestinations() {
        TeleportActionType type = new TeleportActionType();
        assertNotNull(type.parse(obj("{'offset':[0,0,10],'space':'local'}")));
    }

    @Test
    void acceptsRolesFormationMovesAndRandomProfiles() {
        TeleportActionType type = new TeleportActionType();
        assertNotNull(type.parse(obj("{'to':'target','preserve_offset_from':'origin',"
                + "'random':{'radius':[8,3,8],'min_distance':2,'shape':'cylinder','attempts':24}}")));
        assertNotNull(type.parse(obj("{'random':false}")));
    }

    @Test
    void rejectsUnknownDestinationVocabulary() {
        assertNull(new TeleportActionType().parse(obj("{'to':'somewhereish'}")));
    }
}
