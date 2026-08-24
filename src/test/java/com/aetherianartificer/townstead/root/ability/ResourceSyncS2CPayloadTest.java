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
        ResourceSyncS2CPayload.Effect flames = new ResourceSyncS2CPayload.Effect(
                "townstead:flames", 0.9f, 1.4f, 3.6f, 1f, 0xFFF4B0,
                "crosswise", -1, 0xB83218,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                11, 8, 0.55f, "surface");
        ResourceSyncS2CPayload.Effect steam = new ResourceSyncS2CPayload.Effect(
                "townstead:steam", 0.74f, 0.9f, 3.6f, 1f, 0xC8D8DE,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                10, 3, 0.7f);
        ResourceSyncS2CPayload.Effect electric = new ResourceSyncS2CPayload.Effect(
                "townstead:electric", 0.88f, 1.6f, 3.6f, 1f, 0xE8FFFF,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                9, 0.7f, 0.85f);
        ResourceSyncS2CPayload.Effect wisps = new ResourceSyncS2CPayload.Effect(
                "townstead:wisps", 0.76f, 0.95f, 3.6f, 1f, 0xD8FFF2,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                9, 5, 0.85f);
        ResourceSyncS2CPayload.Effect sparkle = new ResourceSyncS2CPayload.Effect(
                "townstead:sparkle", 0.84f, 1.2f, 3.6f, 1f, 0xFFF4CC,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                5, 3, 0.70f,
                10, 4, 0.78f);
        ResourceSyncS2CPayload.Effect crystalline = new ResourceSyncS2CPayload.Effect(
                "townstead:crystalline", 0.78f, 0.9f, 3.6f, 1f, 0xCFF8FF,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                5, 3, 0.70f,
                6, 3, 0.65f,
                11, 4, 0.72f);
        ResourceSyncS2CPayload.Effect runes = new ResourceSyncS2CPayload.Effect(
                "townstead:runes", 0.82f, 1.15f, 3.6f, 1f, 0xE9D2FF,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                5, 3, 0.70f,
                6, 3, 0.65f,
                7, 3, 0.55f,
                "blink", 4, "example:textures/gui/runes.png", 4, 6, 10, 3, 0.78f);
        ResourceSyncS2CPayload.Effect corruption = new ResourceSyncS2CPayload.Effect(
                "townstead:corruption", 0.88f, 1.1f, 3.6f, 1f, 0x4D165E,
                "crosswise", -1, -1,
                12, 0.18f, 0.92f, 0.65f, 0.20f,
                5, 0.78f, 0.55f,
                6, 2, 0.35f,
                8, 0.45f, 0.65f, 0.8f,
                7, 7, 0.8f, "base",
                6, 3, 0.45f,
                5, 0.45f, 0.65f,
                5, 3, 0.70f,
                6, 3, 0.65f,
                7, 3, 0.55f,
                "scroll", 3, "", 3, 5, 8, 2, 0.55f,
                15, 4,
                11, 0.72f, 0.44f,
                13, 3, 0.68f,
                17, 4, 0.52f, "example:textures/gui/motes.png", 3, 4, 9, 3);
        ResourceSyncS2CPayload.Effect voidEffect = new ResourceSyncS2CPayload.Effect(
                "townstead:void", 0.7f, 0.75f, 3.6f, 1f, 0xDCCBFF,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect prismatic = new ResourceSyncS2CPayload.Effect(
                "townstead:prismatic", 0.65f, 0.7f, 3.6f, 1f, -1,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect spores = new ResourceSyncS2CPayload.Effect(
                "townstead:spores", 0.55f, 0.65f, 3.6f, 1f, 0xC9E88A,
                "crosswise", -1, -1);
        ResourceSyncS2CPayload.Effect fallingMotes = new ResourceSyncS2CPayload.Effect(
                "townstead:falling_motes", 0.65f, 0.75f, 3.6f, 1f, 0xF2F7FF,
                "crosswise", -1, -1);
        List<ResourceSyncS2CPayload.Reaction> reactions = List.of(
                new ResourceSyncS2CPayload.Reaction("townstead:gain_flash", 0.75f,
                        0.55f, 1.2f, -1, 0.2f, "flash", 0.25f),
                new ResourceSyncS2CPayload.Reaction("townstead:low_warning", 0.5f,
                        0.9f, 1f, 0xFFE080, 0.18f, "pulse", 0.3f));
        ResourceSyncS2CPayload.Bar bar = new ResourceSyncS2CPayload.Bar(
                "townstead:test", 68, 0, 100, 100, 0x3FA0FF,
                "HORIZONTAL", "CONTINUOUS",
                List.of(gradient, shimmer, pulse, flow, liquid, viscous, bubbles, embers,
                        flames, steam, electric, wisps, sparkle, crystalline, runes, corruption,
                        voidEffect, prismatic, spores, fallingMotes),
                reactions, true, 42,
                "townstead:plain", "townstead:arcane", "TOP_LEFT", "DOTS", 10, 0,
                0xFF202020, 0x3FA0FF, 0xC8F3FF, 1, "", -1);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());

        new ResourceSyncS2CPayload(List.of(bar)).write(buffer);
        ResourceSyncS2CPayload decoded = ResourceSyncS2CPayload.read(buffer);

        assertEquals(List.of(bar), decoded.bars());
    }
}
