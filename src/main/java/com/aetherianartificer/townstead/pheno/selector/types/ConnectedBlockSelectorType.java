package com.aetherianartificer.townstead.pheno.selector.types;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectorType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Flood-selects face-connected blocks, defaulting to the focus block's exact block type. */
public final class ConnectedBlockSelectorType implements BlockSelectorType {
    public static final String KEY = "pheno:connected";
    private static final int DEFAULT_LIMIT = 256;

    @Override public String key() { return KEY; }

    @Override
    public BlockSelector parse(JsonObject json) {
        BlockCondition condition = json.has("condition") ? BlockConditions.parse(json.get("condition")) : null;
        if (json.has("condition") && condition == null) return null;
        int limit = Math.max(1, Math.min(8192, json.has("limit") ? json.get("limit").getAsInt() : DEFAULT_LIMIT));
        return context -> {
            BlockPos origin = context.focusBlock();
            var initial = context.level().getBlockState(origin).getBlock();
            Predicate<BlockPos> match = condition == null
                    ? pos -> context.level().getBlockState(pos).is(initial)
                    : pos -> condition.test(context.level(), pos);
            return connected(context.level(), origin, match, limit);
        };
    }

    public static List<BlockPos> connected(Level level, BlockPos origin,
                                           Predicate<BlockPos> match, int limit) {
        if (level == null || origin == null || match == null || !match.test(origin)) return List.of();
        Set<BlockPos> seen = new LinkedHashSet<>();
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        open.add(origin.immutable());
        while (!open.isEmpty() && seen.size() < limit) {
            BlockPos pos = open.removeFirst();
            if (!seen.add(pos) || !match.test(pos)) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction).immutable();
                if (!seen.contains(next) && match.test(next)) open.addLast(next);
            }
        }
        return new ArrayList<>(seen);
    }
}
