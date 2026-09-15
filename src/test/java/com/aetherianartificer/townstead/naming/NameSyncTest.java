package com.aetherianartificer.townstead.naming;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NameSyncTest {
    @AfterEach void clear() { NameClientStore.clear(); }

    @Test void familyFirstSurvivesPacketAndClientFormatting() {
        var original = new NameSyncPayload(42, "Sato", "", NamingTradition.Order.FAMILY_FIRST,
                NamingTradition.FamilyType.INHERITED, "mca:japan");
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            NameSyncPayload.encode(original, buffer);
            var decoded = NameSyncPayload.decode(buffer);
            assertEquals(original, decoded);
            assertEquals(0, buffer.readableBytes());
            NameClientStore.set(decoded.entityId(), decoded.familyName(), decoded.culture(),
                    decoded.order(), decoded.familyType(), decoded.tradition());
            assertEquals("Sato Hana", NameClientStore.fullName(42, "Hana"));
            assertEquals("Sato Hana", NameClientStore.fullName(42, "Sato Hana"));
            // The editor decides whether the surname field does anything from this alone.
            assertEquals(NamingTradition.FamilyType.INHERITED, NameClientStore.rule(42));
            // The tradition names how the surname is built; a villager in no culture still has one.
            assertEquals("mca:japan", NameClientStore.tradition(42));
            assertEquals("", NameClientStore.culture(42));
        } finally { buffer.release(); }
    }

    @Test void disconnectClearsNamesCulturesAndOrderingForReusedIds() {
        NameClientStore.set(7, "Sato", "townstead_classic:highhold", NamingTradition.Order.FAMILY_FIRST,
                NamingTradition.FamilyType.INHERITED, "mca:japan");
        NameClientStore.clear();
        assertEquals("Alex", NameClientStore.fullName(7, "Alex"));
        assertEquals("", NameClientStore.culture(7));
        // A cleared id must not keep a rule either, or the editor would offer to edit a surname
        // for a villager it has been told nothing about.
        assertEquals(NamingTradition.FamilyType.NONE, NameClientStore.rule(7));
        assertEquals("", NameClientStore.tradition(7));
        NameClientStore.set(7, "Smith", "", null, null, "");
        assertEquals("Alex Smith", NameClientStore.fullName(7, "Alex"));
        assertEquals(NamingTradition.FamilyType.NONE, NameClientStore.rule(7));
    }

    @Test void deliberateExternalSurnameOutranksCultureRegardlessOfSourceOrder() {
        var name = NameParts.builder("Hana").family("Sato")
                .authoritativeFamily("Smith").family("Sato").build();
        assertEquals("Hana Smith", name.fullName());
        assertEquals("Hana", NameParts.builder("Hana").authoritativeFamily("").family("Sato").build().fullName());
    }
}
