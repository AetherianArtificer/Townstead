package com.aetherianartificer.townstead.naming;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameSyncTest {
    @AfterEach void clear() { NameClientStore.clear(); }

    @Test void familyFirstSurvivesPacketAndClientFormatting() {
        var original = new NameSyncPayload(42, "Sato", "mca:japan", NamingTradition.Order.FAMILY_FIRST);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NameSyncPayload.encode(original, buffer);
            var decoded = NameSyncPayload.decode(buffer);
            assertEquals(original, decoded);
            assertEquals(0, buffer.readableBytes());
            NameClientStore.set(decoded.entityId(), decoded.familyName(), decoded.culture(), decoded.order());
            assertEquals("Sato Hana", NameClientStore.fullName(42, "Hana"));
            assertEquals("Sato Hana", NameClientStore.fullName(42, "Sato Hana"));
        } finally { buffer.release(); }
    }

    @Test void disconnectClearsNamesCulturesAndOrderingForReusedIds() {
        NameClientStore.set(7, "Sato", "mca:japan", NamingTradition.Order.FAMILY_FIRST);
        NameClientStore.clear();
        assertEquals("Alex", NameClientStore.fullName(7, "Alex"));
        assertEquals("", NameClientStore.culture(7));
        NameClientStore.set(7, "Smith", "", null);
        assertEquals("Alex Smith", NameClientStore.fullName(7, "Alex"));
    }

    @Test void deliberateExternalSurnameOutranksCultureRegardlessOfSourceOrder() {
        var name = NameParts.builder("Hana").family("Sato")
                .authoritativeFamily("Smith").family("Sato").build();
        assertEquals("Hana Smith", name.fullName());
        assertEquals("Hana", NameParts.builder("Hana").authoritativeFamily("").family("Sato").build().fullName());
    }
}
