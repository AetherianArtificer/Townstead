package com.aetherianartificer.townstead.commands;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class FoundingCommandsTest {
    @Test
    void translationArgumentsConvertRegistryIdsBeforeNetworkEncoding() {
        ResourceLocation profile = ResourceLocation.tryParse("townstead_classic:rosaguarda");
        Component detail = Component.literal("near another village");

        Object[] safe = FoundingCommands.networkSafeTranslationArgs(profile, detail, 12, true);

        assertEquals("townstead_classic:rosaguarda", safe[0]);
        assertSame(detail, safe[1]);
        assertEquals(12, safe[2]);
        assertEquals(true, safe[3]);
    }
}
