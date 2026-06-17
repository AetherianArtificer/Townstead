package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * True when the block at an offset from the entity satisfies a {@code block_condition}
 * (Apoli's {@code block} entity condition). The offset defaults to the entity's own
 * block (its feet); use {@code y:-1} for the block it stands on.
 *
 * <p>JSON: {@code { "type":"pheno:block", "y":-1,
 * "block_condition":{ "type":"pheno:in_tag", "tag":"minecraft:sand" } }}</p>
 */
public final class BlockAtConditionType implements ConditionType {

    public static final String KEY = "pheno:block";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        int x = GsonHelper.getAsInt(json, "x", 0);
        int y = GsonHelper.getAsInt(json, "y", 0);
        int z = GsonHelper.getAsInt(json, "z", 0);
        BlockCondition block = BlockConditions.parse(json.get("block_condition"));
        if (block == null) return null;
        return ctx -> block.test(ctx.level(), ctx.pos().offset(x, y, z));
    }
}
