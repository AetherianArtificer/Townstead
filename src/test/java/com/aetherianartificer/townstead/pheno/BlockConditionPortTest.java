package com.aetherianartificer.townstead.pheno;

import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BlockConditionPortTest {

    @Test
    void blockShapeAcceptsAValidMinecraftUnitBox() {
        assertNotNull(BlockConditions.parse(JsonParser.parseString("""
                {"type":"pheno:block_shape","box":[3,0,3,13,1,13]}
                """)));
    }

    @Test
    void blockShapeRejectsMalformedOrEmptyBoxes() {
        assertNull(BlockConditions.parse(JsonParser.parseString("""
                {"type":"pheno:block_shape","box":[3,0,3,13,1]}
                """)));
        assertNull(BlockConditions.parse(JsonParser.parseString("""
                {"type":"pheno:block_shape","box":[3,0,3,3,1,13]}
                """)));
    }
}
