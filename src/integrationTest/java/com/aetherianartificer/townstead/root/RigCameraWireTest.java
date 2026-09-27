package com.aetherianartificer.townstead.root;
import com.aetherianartificer.townstead.root.rig.RigDefinition;
import com.aetherianartificer.townstead.root.rig.RigJsonLoader;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RigCameraWireTest {
    @Test void cameraOffsetAndCustomArmorRoundTripWithoutConsumingNextField() throws Exception {
        var parse = RigJsonLoader.class.getDeclaredMethod("parse", String.class, JsonObject.class);
        parse.setAccessible(true);
        var rig = (RigDefinition) parse.invoke(null, "test:frog", JsonParser.parseString("""
          {"model":{"type":"geometry","file":"test:geo/frog.geo.json"},
          "camera":{"bone":"body","height_offset":8},
          "hitbox":{"width":0.5,"height":0.75,"crouch_height":0.625,"swim_height":0.4,"eye_height":0.625,"scale_with_entity":false},
          "wearables":{"back":{"offset":[0,-3.5,4],"scale":0.5,"items":{"backpack":{"scale":0.8,"scale_axes":[1.05,0.3,1.9]}}}},
          "armor":{"type":"custom","inner":"test:geo/inner.geo.json","outer":"test:geo/outer.geo.json"}}
          """).getAsJsonObject());
        var packet = new RootCatalogSyncPayload(List.of(),List.of(),List.of(),List.of(rig),List.of(),List.of());
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.write(buf); buf.writeInt(0x12345678);
            var result = RootCatalogSyncPayload.read(buf).rigs().get(0);
            assertEquals(8f, result.cameraHeightOffset());
            assertEquals(new RigDefinition.Hitbox(.5f,.75f,.625f,.4f,.625f,false), result.hitbox());
            assertEquals("body", result.cameraBone());
            assertEquals(RigDefinition.ArmorType.CUSTOM, result.armorType());
            assertEquals("test:geo/outer.geo.json", result.armorOuter());
            assertEquals(.5F, result.back().base().scale());
            assertEquals(.4F, result.back().forItem("backpack").scale(), .0001F);
            assertArrayEquals(new float[]{1.05F,.3F,1.9F},result.back().forItem("backpack").scaleAxes());
            assertEquals(0x12345678, buf.readInt());
            assertEquals(0,buf.readableBytes());
        } finally { buf.release(); }
    }
}
