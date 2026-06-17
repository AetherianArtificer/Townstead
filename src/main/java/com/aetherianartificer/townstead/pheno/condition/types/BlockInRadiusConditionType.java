package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Comparison;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;

/**
 * Compares the number of blocks within {@code radius} matching a {@code block_condition}
 * against {@code compare_to} using {@code comparison} (Apoli's {@code block_in_radius} /
 * {@code in_block_anywhere}). The default ({@code >= 1}) means "any block in range matches"
 * and early-exits. {@code radius} is capped at {@value #MAX_RADIUS} since this scans a cube
 * each evaluation; keep it small on per-tick gates.
 *
 * <p>JSON: {@code { "type":"pheno:block_in_radius", "radius":4,
 * "block_condition":{ "type":"pheno:fluid", "fluid":"lava" } }}</p>
 */
public final class BlockInRadiusConditionType implements ConditionType {

    public static final String KEY = "pheno:block_in_radius";

    private static final int MAX_RADIUS = 8;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        BlockCondition block = BlockConditions.parse(json.get("block_condition"));
        if (block == null) return null;
        int radius = Mth.clamp(GsonHelper.getAsInt(json, "radius", 4), 0, MAX_RADIUS);
        Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
        int compareTo = GsonHelper.getAsInt(json, "compare_to", 1);
        boolean earlyExit = compareTo > 0
                && (comparison == Comparison.GREATER_OR_EQUAL || comparison == Comparison.GREATER);
        return ctx -> {
            BlockPos center = ctx.pos();
            int count = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                    center.offset(radius, radius, radius))) {
                if (!block.test(ctx.level(), pos)) continue;
                count++;
                if (earlyExit && count >= compareTo) return true;
            }
            return comparison.compare(count, compareTo);
        };
    }
}
