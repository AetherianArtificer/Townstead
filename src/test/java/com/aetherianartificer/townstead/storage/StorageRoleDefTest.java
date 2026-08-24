package com.aetherianartificer.townstead.storage;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageRoleDefTest {
    @Test
    void namespaceSelectorsMayDeclareExplicitExceptions() {
        StorageRoleDef def = StorageRoleDef.parse(id("test:machines"),
                JsonParser.parseString("""
                        {
                          "schema":"townstead:storage_role/v1",
                          "role":"not_storage",
                          "namespaces":["examplemod"],
                          "except":["examplemod:warehouse", "#examplemod:shelves"]
                        }
                        """).getAsJsonObject());

        assertNotNull(def);
        assertEquals(StorageRoleDef.Role.NOT_STORAGE, def.role());
        assertEquals(java.util.Set.of("examplemod"), def.namespaces());
        assertEquals(java.util.Set.of(id("examplemod:warehouse")), def.exceptions());
        assertEquals(java.util.List.of(id("examplemod:shelves")), def.exceptionTags());
    }

    @Test
    void emptySelectorDocumentIsRejected() {
        assertNull(StorageRoleDef.parse(id("test:empty"),
                JsonParser.parseString("{\"role\":\"storage\"}").getAsJsonObject()));
    }

    private static ResourceLocation id(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
