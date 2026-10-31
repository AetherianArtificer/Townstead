package com.aetherianartificer.townstead.hunger;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Checks the actual Minecraft integration points that can replace a villager's held serving. */
class ServingHandIntegrationTest {
    @Test void tradePreviewAndCleanupBothWriteThroughTheProtectedEquipmentSetter() throws Exception {
        ClassNode trades = read("net/minecraft/world/entity/ai/behavior/ShowTradesToPlayer.class");
        for (String name : new String[]{"displayAsHeldItem", "clearHeldItem"}) {
            var method = trades.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            boolean writesHand = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && call.name.equals("setItemSlot")) writesHand = true;
            }
            assertTrue(writesHand, name + " must pass through our serving guard");
        }
        assertTrue(read("net/minecraft/world/entity/Mob.class").methods.stream().anyMatch(m ->
                m.name.equals("setItemSlot") && m.desc.equals(
                        "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V")));
    }

    @Test void sipAssetCannotLoopWithoutAnotherRealSipRequest() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/townstead_performance/animations/townstead/sip.animation.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(json.getAsJsonObject("animations").getAsJsonObject("animation.sip").get("loop").getAsBoolean());
        }
    }

    @Test void sipsHaveAtLeastTwentySecondsBetweenStarts() {
        for (int seed : new int[]{0, 4351, -934611, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            int lastStart = -1000;
            boolean previous = false;
            for (int tick = 0; tick < RecreationalDrinkTiming.HOLD_TICKS; tick++) {
                boolean sipping = RecreationalDrinkTiming.sipping(tick, 32, seed);
                assertEquals(sipping && !previous, RecreationalDrinkTiming.startsSip(tick, seed),
                        "an animation request is emitted only at the edge, never every frame of a sip");
                if (sipping && !previous) {
                    assertTrue(tick - lastStart >= 400);
                    lastStart = tick;
                }
                previous = sipping;
            }
        }
    }

    private static ClassNode read(String resource) throws Exception {
        try (var stream = ServingHandIntegrationTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            ClassNode type = new ClassNode();
            new ClassReader(stream).accept(type, 0);
            return type;
        }
    }
}
