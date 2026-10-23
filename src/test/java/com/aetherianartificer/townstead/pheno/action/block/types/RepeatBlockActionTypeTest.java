package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RepeatBlockActionTypeTest {
    @Test
    void repeatsOnlyWithinTheAuthoredBound() {
        AtomicInteger calls = new AtomicInteger();
        BlockActionTypes.register(new com.aetherianartificer.townstead.pheno.action.block.BlockActionType() {
            @Override public String key() { return "test:count_protocol_pulse"; }
            @Override public com.aetherianartificer.townstead.pheno.action.block.BlockAction parse(
                    com.google.gson.JsonObject json) { return context -> calls.incrementAndGet(); }
        });
        BlockActionTypes.register(new RepeatBlockActionType());
        var action = BlockActions.parse(JsonParser.parseString("""
                {"type":"pheno:repeat","times":4,
                 "block_action":{"type":"test:count_protocol_pulse"}}
                """));
        assertNotNull(action);
        action.run(new BlockActionContext(null, new BlockPos(0, 0, 0)));
        assertEquals(4, calls.get());
    }

    @Test
    void rejectsZeroFractionalAndUnboundedCounts() {
        RepeatBlockActionType type = new RepeatBlockActionType();
        assertNull(type.parse(object(0)));
        assertNull(type.parse(object(65)));
        assertNull(type.parse(JsonParser.parseString("""
                {"times":1.5,"block_action":{"type":"test:none"}}
                """).getAsJsonObject()));
    }

    private static com.google.gson.JsonObject object(int times) {
        return JsonParser.parseString("{\"times\":" + times
                + ",\"block_action\":{\"type\":\"test:none\"}}").getAsJsonObject();
    }
}
