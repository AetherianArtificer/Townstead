package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.action.block.BlockActions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

/**
 * Runs the wrapped block action at every block within {@code radius} of the target
 * (Apoli's block {@code area_of_effect}). {@code shape} is {@code cube} (default) or
 * {@code sphere}; an optional {@code block_condition} filters which blocks are affected.
 * Radius is capped to keep the volume bounded.
 *
 * <p>JSON: {@code { "type":"pheno:area_of_effect", "radius":2, "shape":"sphere",
 * "block_condition":{ "type":"pheno:in_tag", "tag":"minecraft:crops" },
 * "block_action":{ "type":"pheno:bonemeal" } }}</p>
 */
public final class AreaOfEffectBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:area_of_effect";

    private static final int MAX_RADIUS = 16;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        int radius = Math.max(0, Math.min(MAX_RADIUS, GsonHelper.getAsInt(json, "radius", 1)));
        boolean sphere = "sphere".equalsIgnoreCase(GsonHelper.getAsString(json, "shape", "cube"));
        BlockAction inner = BlockActions.parse(json.get("block_action"));
        if (inner == null) return null;
        @Nullable BlockCondition filter = json.has("block_condition")
                ? BlockConditions.parse(json.get("block_condition")) : null;
        return ctx -> {
            BlockPos center = ctx.pos();
            int r2 = radius * radius;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (sphere && dx * dx + dy * dy + dz * dz > r2) continue;
                        BlockPos at = center.offset(dx, dy, dz);
                        if (filter != null && !filter.test(ctx.level(), at)) continue;
                        inner.run(ctx.at(at));
                    }
                }
            }
        };
    }
}
