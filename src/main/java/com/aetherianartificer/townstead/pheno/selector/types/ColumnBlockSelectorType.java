package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectorType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects an ordered straight run of blocks away from the current block focus. */
public final class ColumnBlockSelectorType implements BlockSelectorType {
    public static final String KEY = "pheno:column";

    @Override public String key() { return KEY; }

    @Override
    public BlockSelector parse(JsonObject json) {
        Direction direction;
        try {
            direction = Direction.valueOf(GsonHelper.getAsString(json, "direction", "down")
                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        int distance = GsonHelper.getAsInt(json, "distance", 1);
        if (distance < 1 || distance > 64) return null;
        BlockCondition where = json.has("where") ? BlockConditions.parse(json.get("where")) : null;
        if (json.has("where") && where == null) return null;
        int limit = Math.max(0, GsonHelper.getAsInt(json, "limit", 0));
        return context -> {
            List<BlockPos> result = new ArrayList<>();
            BlockPos origin = context.focusBlock();
            for (int step = 1; step <= distance; step++) {
                BlockPos candidate = origin.relative(direction, step);
                if (where != null && !where.test(context.level(), candidate)) continue;
                result.add(candidate.immutable());
                if (limit > 0 && result.size() >= limit) break;
            }
            return List.copyOf(result);
        };
    }
}
