package com.aetherianartificer.townstead.root.ability;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceSyncS2CPayloadTest {

    @Test
    void orderedEffectStackSurvivesNetworkRoundTrip() {
        ResourceSyncS2CPayload.Effect gradient = new ResourceSyncS2CPayload.Effect(
                "townstead:gradient", 0.55f, 1f, 3.6f, 1f, -1,
                "leading_edge", 0xE8FFFF, 0x163A66);
        ResourceSyncS2CPayload.Effect shimmer = new ResourceSyncS2CPayload.Effect(
                "townstead:shimmer", 0.42f, 1.25f, 5.5f, 1f, 0xFFF4CC,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect pulse = new ResourceSyncS2CPayload.Effect(
                "townstead:pulse", 0.2f, 0.8f, 3.6f, 1f, -1,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect flow = new ResourceSyncS2CPayload.Effect(
                "townstead:flow", 0.3f, 1.1f, 3.6f, 1f, 0xA8ECFF,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect liquid = new ResourceSyncS2CPayload.Effect(
                "townstead:liquid", 0.4f, 0.9f, 3.6f, 1f, 0xD8B4FF,
                "crosswise", -1, -1,
                16, 0.28f, 0.94f, 0.8f, 0.35f);
        ResourceSyncS2CPayload.Effect viscous = new ResourceSyncS2CPayload.Effect(
                "townstead:viscous", 0.6f, 0.7f, 3.6f, 1f, 0xB7FF74,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                7, 0.86f, 0.72f);
        ResourceSyncS2CPayload.Effect bubbles = new ResourceSyncS2CPayload.Effect(
                "townstead:bubbles", 0.7f, 1.15f, 3.6f, 1f, 0xE8FFFF,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                9, 3, 0.6f);
        ResourceSyncS2CPayload.Effect embers = new ResourceSyncS2CPayload.Effect(
                "townstead:embers", 0.82f, 1.3f, 3.6f, 1f, 0xFFF0A0,
                "crosswise", -1, 0xA82B18,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                12, 0.7f, 0.4f, 0.9f);
        ResourceSyncS2CPayload.Bar bar = new ResourceSyncS2CPayload.Bar(
                "townstead:test", 68, 0, 100, 100, 0x3FA0FF,
                "HORIZONTAL", "CONTINUOUS",
                List.of(gradient, shimmer, pulse, flow, liquid, viscous, bubbles, embers),
                "townstead:plain", "townstead:arcane", "TOP_LEFT", "DOTS", 10, 0,
                0xFF202020, 0x3FA0FF, 0xC8F3FF, 1, "", -1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new ResourceSyncS2CPayload(List.of(bar)).write(buffer);
        ResourceSyncS2CPayload decoded = ResourceSyncS2CPayload.read(buffer);

        assertEquals(List.of(bar), decoded.bars());
    }
}
