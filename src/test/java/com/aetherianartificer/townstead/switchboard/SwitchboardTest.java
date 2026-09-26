package com.aetherianartificer.townstead.switchboard;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.neoforged.neoforge.common.ModConfigSpec;
//?} else if forge {
/*import net.minecraftforge.common.ForgeConfigSpec;
*///?}
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

class SwitchboardTest {
    enum Mode { OFF, TRAIT, EVERYONE }

    private static Supplier<Boolean> hunger;
    private static Supplier<Integer> radius;
    private static Supplier<Mode> mode;

    @BeforeAll static void spec() {
        //? if neoforge {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        //?} else if forge {
        /*ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        *///?}
        b.push("needs").push("hunger");
        hunger = b.define("enableVillagerHunger", true);
        b.pop().pop().push("farming");
        radius = b.defineInRange("farmerFarmRadius", 12, 4, 32);
        mode = b.defineEnum("mode", Mode.OFF);
        b.pop();
        SettingIndex.useSpec(b.build());
    }

    @AfterEach void clear() { Switchboard.clear(); }

    @Test void indexesByTomlPath() {
        assertNotNull(SettingIndex.get("needs.hunger.enableVillagerHunger"));
        assertNotNull(SettingIndex.get("farming.farmerFarmRadius"));
        assertNull(SettingIndex.get("needs.hunger"));
    }

    @Test void overrideWinsWithoutTheConfigLoaded() {
        Switchboard.apply(Map.of("needs.hunger.enableVillagerHunger", new JsonPrimitive(false),
                "farming.farmerFarmRadius", new JsonPrimitive(20),
                "farming.mode", new JsonPrimitive("trait")));
        assertFalse(Switchboard.get(hunger));
        assertEquals(20, Switchboard.get(radius));
        assertEquals(Mode.TRAIT, Switchboard.get(mode));
        assertEquals(false, Switchboard.valueAt(List.of("needs", "hunger", "enableVillagerHunger")));
    }

    @Test void dropsUnknownKeysAndInvalidValues() {
        Switchboard.apply(Map.of("no.such.setting", new JsonPrimitive(true),
                "farming.farmerFarmRadius", new JsonPrimitive(99),
                "farming.mode", new JsonPrimitive("sometimes")));
        assertTrue(Switchboard.overrides().isEmpty());
    }

    @Test void lockedPackValuesBeatTheWorld() {
        Map<String, JsonElement> pack = Map.of("a", new JsonPrimitive(1), "b", new JsonPrimitive(2));
        Map<String, JsonElement> world = Map.of("a", new JsonPrimitive(10), "b", new JsonPrimitive(20),
                "c", new JsonPrimitive(30));
        Map<String, JsonElement> out = SwitchboardServer.effective(pack, Set.of("a"), world);
        assertEquals(1, out.get("a").getAsInt());
        assertEquals(20, out.get("b").getAsInt());
        assertEquals(30, out.get("c").getAsInt());
    }

    @Test void contentKeysParseAndDefault() {
        String state = WorldKeys.rootState("townstead_mobs:zombie");
        String group = WorldKeys.groupOn("ancestry", "townstead_mobs:risen");
        String rate = WorldKeys.groupRate("pack", "townstead_mobs");
        assertEquals(WorldKeys.RootState.VILLAGERS, WorldKeys.parse(state, new JsonPrimitive("villagers")));
        assertEquals(WorldKeys.RootState.DISCOVERABLE, WorldKeys.parse(state, new JsonPrimitive("discoverable")));
        assertNull(WorldKeys.parse(state, new JsonPrimitive("sometimes")));
        assertEquals("ancestry", WorldKeys.groupDimension(group));
        assertEquals("townstead_mobs:risen", WorldKeys.subject(group));
        assertNull(WorldKeys.parse(rate, new JsonPrimitive(0)));
        assertNull(WorldKeys.parse(rate, new JsonPrimitive(50)));
        assertNull(WorldKeys.groupDimension("roots.group.weather.rain"));

        assertEquals(WorldKeys.RootState.EVERYONE, Switchboard.content(state));
        Switchboard.apply(Map.of(state, new JsonPrimitive("OFF"), rate, new JsonPrimitive(2.5), group, new JsonPrimitive(false)));
        assertEquals(WorldKeys.RootState.OFF, Switchboard.content(state));
        assertEquals(2.5, Switchboard.content(rate));
        assertEquals(false, Switchboard.content(group));
        assertEquals(3, Switchboard.overrides().size());
    }

    @Test void presetShareCodeRoundTrips() {
        var preset = new SwitchboardPreset("Hardcore", "No mercy",
                Map.of("farming.farmerFarmRadius", new JsonPrimitive(20), "needs.hunger.enableVillagerHunger", new JsonPrimitive(false)));
        String code = preset.toShareCode();
        assertTrue(code.startsWith(SwitchboardPreset.CODE_PREFIX));
        assertEquals(preset, SwitchboardPreset.parse(code, "x"));
        assertEquals(preset, SwitchboardPreset.parse(preset.toJson().toString(), "x"));
    }

    @Test void presetParseRejectsJunk() {
        assertThrows(IllegalArgumentException.class, () -> SwitchboardPreset.parse("TS1:@@@", "x"));
        assertThrows(IllegalArgumentException.class, () -> SwitchboardPreset.parse("{\"name\":\"no values\"}", "x"));
        assertThrows(IllegalArgumentException.class, () -> SwitchboardPreset.parse("<html>hello</html>", "x"));
        assertEquals("Fallback", SwitchboardPreset.parse("{\"values\":{}}", "Fallback").name());
    }

    @Test void syncPayloadRoundTrips() {
        var payload = SwitchboardSyncS2CPayload.of(Map.of("needs.hunger.enableVillagerHunger", new JsonPrimitive(false)));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            payload.write(buffer);
            SwitchboardSyncS2CPayload.read(buffer).apply();
            assertFalse(Switchboard.get(hunger));
        } finally { buffer.release(); }
    }
}
