package com.aetherianartificer.townstead.block.haze;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HazeKindsTest {

    @Test
    void syncKeepsOrderAndEveryClientField() {
        HazeKind flour = new HazeKind(id("townstead:flour"), HazeKind.Shape.CLOUD, 0xEDE6D6, 200, true, 2.5f,
                id("minecraft:white_ash"), null, null, null, 10);
        HazeKind jam = new HazeKind(id("townstead:jam"), HazeKind.Shape.LAYER, 0x801A34, 320, false, 0f,
                null, id("townstead:haze/jam"), null, null, 5);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        new HazeKindsSyncPayload(List.of(flour, jam)).write(buffer);
        List<HazeKind> read = HazeKindsSyncPayload.read(buffer).kinds();

        assertEquals(2, read.size());
        assertEquals(flour.id(), read.get(0).id(), "the index a block state stores must survive the trip");
        assertEquals(HazeKind.Shape.CLOUD, read.get(0).shape());
        assertTrue(read.get(0).conceals());
        assertEquals(2.5f, read.get(0).fogDistance());
        assertEquals(flour.particle(), read.get(0).particle());
        assertEquals(jam.texture(), read.get(1).texture());
        assertEquals(0x801A34, read.get(1).color());
        assertNull(read.get(1).insideAction(), "pheno behaviour stays on the server");
    }

    @Test
    void bundledKindsDeclareAShapeTheBlockUnderstands() throws Exception {
        for (String name : new String[]{"flour", "sugar", "smoke", "jam", "bramble", "straw", "mud", "net", "ale", "coffee", "holy_water", "incense", "embers", "rubble", "stone_wall", "snare", "frost", "ice_wall", "silk", "miasma", "grapevine"}) {
            try (var in = HazeKindsTest.class.getResourceAsStream("/data/townstead/haze/" + name + ".json")) {
                assertNotNull(in, "bundled haze kind missing: " + name);
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                        .getAsJsonObject();
                HazeKind.Shape shape = HazeKind.Shape.valueOf(json.get("shape").getAsString().toUpperCase());
                if (shape == HazeKind.Shape.SOLID) {
                    assertTrue(json.has("texture"), name + ": a wall needs a texture to be seen");
                } else if (shape.grounded()) {
                    assertTrue(json.has("inside"), name + ": a grounded kind exists to act on what stands in it");
                } else {
                    assertTrue(json.has("fog"), name + ": a cloud without fog is invisible from inside");
                }
            }
        }
    }

    @Test
    void stepTicksSpreadTheDurationOverEveryDensity() {
        HazeKind kind = new HazeKind(id("townstead:test"), HazeKind.Shape.CLOUD, 0, 200, false, 0f,
                null, null, null, null, 10);
        assertEquals(200, kind.stepTicks() * HazeBlock.MAX_DENSITY);
    }

    private static ResourceLocation id(String raw) {
        return ResourceLocation.tryParse(raw);
    }
}
