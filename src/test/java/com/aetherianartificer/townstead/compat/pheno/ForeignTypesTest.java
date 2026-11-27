package com.aetherianartificer.townstead.compat.pheno;

import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.action.ActionTypes;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionTypes;
import com.aetherianartificer.townstead.pheno.condition.ConditionTypes;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionType;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityConditionTypes;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The bridge to the other copy of Pheno. Pheno is not on the test classpath, so these point the bridge at this copy
 * itself: a type registered here is then found as if it came from the other copy, and runs through the full crossing.
 */
class ForeignTypesTest {

    private static final String THIS_COPY = "com.aetherianartificer.townstead.pheno.";
    private static final String OTHER_COPY = "com.aetherianartificer.pheno.";

    @AfterEach
    void pointBack() {
        ForeignTypes.pointAt(OTHER_COPY);
    }

    @Test
    void aKeyNoCopyKnowsIsEmptyWithoutTheOtherCopy() {
        assertTrue(ActionTypes.get("test:bridge_nowhere").isEmpty());
        assertTrue(ConditionTypes.get("test:bridge_nowhere").isEmpty());
    }

    @Test
    void thisCopysOwnTypeComesFirst() {
        ActionType mine = new ActionType() {
            @Override public String key() { return "test:bridge_mine"; }
            @Override public com.aetherianartificer.townstead.pheno.action.Action parse(JsonObject json) { return ctx -> {}; }
        };
        ActionTypes.register(mine);
        ForeignTypes.pointAt(THIS_COPY);
        assertSame(mine, ActionTypes.get("test:bridge_mine").orElseThrow());
    }

    @Test
    void aKeyNeitherCopyKnowsDoesNotLoop() {
        ForeignTypes.pointAt(THIS_COPY);
        assertTrue(ForeignTypes.action("test:bridge_nowhere").isEmpty());
        assertTrue(ActionTypes.get("test:bridge_nowhere").isEmpty());
    }

    @Test
    void aBlockActionRunsAcrossAndItsOutcomeComesBack() {
        AtomicReference<BlockPos> seen = new AtomicReference<>();
        BlockActionTypes.register(new BlockActionType() {
            @Override public String key() { return "test:bridge_block"; }
            @Override public BlockAction parse(JsonObject json) {
                return ctx -> {
                    seen.set(ctx.pos());
                    if (ctx.blockRoles().containsKey("spots")) ctx.fail();
                };
            }
        });
        ForeignTypes.pointAt(THIS_COPY);
        BlockActionType bridged = ForeignTypes.blockAction("test:bridge_block").orElseThrow();
        assertNotSame(BlockActionTypes.own("test:bridge_block").orElseThrow(), bridged);
        BlockAction action = bridged.parse(new JsonObject());

        BlockPos pos = new BlockPos(3, 4, 5);
        BlockActionContext plain = new BlockActionContext(null, pos);
        action.run(plain);
        assertEquals(pos, seen.get());
        assertTrue(plain.succeeded());

        BlockActionContext withRoles = new BlockActionContext(null, pos).withBlockRoles(Map.of("spots", List.of(pos)));
        action.run(withRoles);
        assertFalse(withRoles.succeeded(), "the other copy's failure comes back");
    }

    @Test
    void aBiEntityConditionAnswersAcross() {
        BiEntityConditionTypes.register(new BiEntityConditionType() {
            @Override public String key() { return "test:bridge_pair"; }
            @Override public BiEntityCondition parse(JsonObject json) {
                boolean want = json.has("want") && json.get("want").getAsBoolean();
                return (actor, target) -> want;
            }
        });
        ForeignTypes.pointAt(THIS_COPY);
        JsonObject yes = new JsonObject();
        yes.addProperty("want", true);
        assertTrue(ForeignTypes.biEntityCondition("test:bridge_pair").orElseThrow().parse(yes).test(null, null));
        assertFalse(ForeignTypes.biEntityCondition("test:bridge_pair").orElseThrow().parse(new JsonObject()).test(null, null));
    }

    @Test
    void theOtherCopysParseErrorComesThroughAsItIs() {
        ActionTypes.register(new ActionType() {
            @Override public String key() { return "test:bridge_strict"; }
            @Override public com.aetherianartificer.townstead.pheno.action.Action parse(JsonObject json) {
                throw new IllegalArgumentException("needs a size");
            }
        });
        ForeignTypes.pointAt(THIS_COPY);
        ActionType bridged = ForeignTypes.action("test:bridge_strict").orElseThrow();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> bridged.parse(new JsonObject()));
        assertEquals("needs a size", e.getMessage());
    }

    @Test
    void aConditionWithNoEntityIsFalse() {
        ConditionTypes.register(new com.aetherianartificer.townstead.pheno.condition.ConditionType() {
            @Override public String key() { return "test:bridge_always"; }
            @Override public com.aetherianartificer.townstead.pheno.condition.Condition parse(JsonObject json) { return ctx -> true; }
        });
        ForeignTypes.pointAt(THIS_COPY);
        var condition = ForeignTypes.condition("test:bridge_always").orElseThrow().parse(new JsonObject());
        var subject = new com.aetherianartificer.townstead.pheno.condition.ConditionContext(
                (com.aetherianartificer.townstead.pheno.condition.PhenoSubject) null);
        assertFalse(condition.test(subject));
    }
}
