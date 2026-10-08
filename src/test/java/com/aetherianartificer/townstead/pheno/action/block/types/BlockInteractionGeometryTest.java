package com.aetherianartificer.townstead.pheno.action.block.types;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockInteractionGeometryTest {
    @Test
    void defaultsToTheTopFaceCenter() {
        BlockInteractionGeometry geometry = BlockInteractionGeometry.parse(JsonParser.parseString(
                "{\"type\":\"pheno:use_block\"}").getAsJsonObject());
        assertNotNull(geometry);
        assertEquals(Direction.UP, geometry.face());
        assertEquals(4.5d, geometry.hit(new BlockPos(4, 7, 9)).getLocation().x);
        assertEquals(7.5d, geometry.hit(new BlockPos(4, 7, 9)).getLocation().y);
    }

    @Test
    void parsesPreciseHitAndActorContext() {
        BlockInteractionGeometry geometry = BlockInteractionGeometry.parse(JsonParser.parseString("""
                {"face":"north","hit":[0.25,0.75,0.0],"inside":true,
                 "actor_offset":[0.0,0.0,2.0],"actor_facing":"north"}
                """).getAsJsonObject());
        assertNotNull(geometry);
        assertEquals(Direction.NORTH, geometry.face());
        assertEquals(Direction.NORTH, geometry.actorFacing());
        assertTrue(geometry.inside());
        assertEquals(2.25d, geometry.hit(new BlockPos(2, 3, 4)).getLocation().x);
        assertEquals(6.5d, geometry.actorPosition(new BlockPos(2, 3, 4), null).z);
    }

    @Test
    void rejectsUnsafeOrMalformedGeometry() {
        assertNull(parse("{\"face\":\"diagonal\"}"));
        assertNull(parse("{\"hit\":[0.5,1.1,0.5]}"));
        assertNull(parse("{\"actor_offset\":[0,0,5]}"));
        assertNull(parse("{\"hit\":[0.5,0.5]}"));
    }

    private static BlockInteractionGeometry parse(String json) {
        return BlockInteractionGeometry.parse(JsonParser.parseString(json).getAsJsonObject());
    }
}
