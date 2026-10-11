package com.aetherianartificer.townstead.data;

import com.google.gson.JsonParser;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A pack's name falls back to its qualifier, which falls back to its namespace. */
class PackInfoTest {

    @Test
    void nameAndQualifierComeFromThePackFile() {
        PackInfo.Info info = PackInfo.parse("runic_roots", JsonParser.parseString("""
                {"qualifier": {"translate": "pack.runic_roots.qualifier"},
                 "name": {"translate": "pack.runic_roots.name"}}""").getAsJsonObject(),
                java.util.Map.of("pack.runic_roots.name", "Runic Roots"));
        assertEquals("pack.runic_roots.name", ((TranslatableContents) info.name().getContents()).getKey());
        assertEquals("pack.runic_roots.qualifier", ((TranslatableContents) info.qualifier().getContents()).getKey());
        assertEquals("Runic Roots", ((TranslatableContents) info.name().getContents()).getFallback(),
                "the English text rides along for a client without the pack's lang");
    }

    @Test
    void aPackWithoutANameIsCalledByItsQualifier() {
        PackInfo.Info info = PackInfo.parse("runic_roots",
                JsonParser.parseString("{\"qualifier\": \"Runic\"}").getAsJsonObject(), java.util.Map.of());
        assertEquals("Runic", info.name().getString());
    }

    @Test
    void aPackWithoutAFileIsCalledByItsNamespace() {
        assertEquals("Classic", PackInfo.fallback("townstead_classic").name().getString());
        assertEquals("Runic Roots", PackInfo.of("runic_roots").qualifier().getString());
    }
}
