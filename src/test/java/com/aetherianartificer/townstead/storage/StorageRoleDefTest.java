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

    @Test
    void semanticRoleNamesAndCompatibilityAliasesParse() {
        assertEquals(StorageRoleDef.Role.INPUTS, parseRole("inputs"));
        assertEquals(StorageRoleDef.Role.OUTPUTS, parseRole("finished_goods"));
        assertEquals(StorageRoleDef.Role.TOOLS, parseRole("tools"));
        assertEquals(StorageRoleDef.Role.RESERVES, parseRole("reserves"));
        assertEquals(StorageRoleDef.Role.PERSONAL, parseRole("personal_storage"));
    }

    @Test
    void roleRoutingKeepsSourcesDestinationsAndPersonalStorageSeparate() {
        assertEquals(0, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.INPUTS),
                StorageUse.INGREDIENT));
        assertEquals(Integer.MAX_VALUE, StorageRoles.useRank(
                java.util.Set.of(StorageRoleDef.Role.INPUTS), StorageUse.OUTPUT));
        assertEquals(0, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.OUTPUTS),
                StorageUse.OUTPUT));
        assertEquals(Integer.MAX_VALUE, StorageRoles.useRank(
                java.util.Set.of(StorageRoleDef.Role.OUTPUTS), StorageUse.INGREDIENT));
        assertEquals(0, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.TOOLS),
                StorageUse.TOOL));
        assertEquals(0, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.TOOLS),
                StorageUse.TOOL_RETURN));
        assertEquals(Integer.MAX_VALUE, StorageRoles.useRank(
                java.util.Set.of(StorageRoleDef.Role.TOOLS), StorageUse.OUTPUT));
        assertEquals(Integer.MAX_VALUE, StorageRoles.useRank(
                java.util.Set.of(StorageRoleDef.Role.TOOLS), StorageUse.INGREDIENT));
        assertEquals(2, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.RESERVES),
                StorageUse.INGREDIENT));
        assertEquals(Integer.MAX_VALUE, StorageRoles.useRank(
                java.util.Set.of(StorageRoleDef.Role.PERSONAL), StorageUse.INGREDIENT));
        assertEquals(0, StorageRoles.useRank(java.util.Set.of(StorageRoleDef.Role.PERSONAL),
                StorageUse.PERSONAL));
    }

    private static StorageRoleDef.Role parseRole(String role) {
        StorageRoleDef def = StorageRoleDef.parse(id("test:" + role),
                JsonParser.parseString("{\"role\":\"" + role
                        + "\",\"blocks\":[\"minecraft:chest\"]}").getAsJsonObject());
        assertNotNull(def);
        return def.role();
    }

    private static ResourceLocation id(String value) {
        //? if >=1.21 {
        return ResourceLocation.parse(value);
        //?} else {
        /*return new ResourceLocation(value);
        *///?}
    }
}
