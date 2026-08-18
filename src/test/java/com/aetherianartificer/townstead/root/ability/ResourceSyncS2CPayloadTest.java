package com.aetherianartificer.townstead.root.ability;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceSyncS2CPayloadTest {

    @Test
    void gradientOptionsSurviveNetworkRoundTrip() {
        ResourceSyncS2CPayload.Effect gradient = new ResourceSyncS2CPayload.Effect(
                "townstead:gradient", 0.55f, "leading_edge", 0xE8FFFF, 0x163A66);
        ResourceSyncS2CPayload.Bar bar = new ResourceSyncS2CPayload.Bar(
                "townstead:test", 68, 0, 100, 100, 0x3FA0FF,
                "HORIZONTAL", "CONTINUOUS", List.of(gradient),
                "townstead:plain", "townstead:arcane", "TOP_LEFT", "DOTS", 10, 0,
                0xFF202020, 0x3FA0FF, 0xC8F3FF, 1, "", -1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new ResourceSyncS2CPayload(List.of(bar)).write(buffer);
        ResourceSyncS2CPayload decoded = ResourceSyncS2CPayload.read(buffer);

        assertEquals(List.of(bar), decoded.bars());
    }
}
