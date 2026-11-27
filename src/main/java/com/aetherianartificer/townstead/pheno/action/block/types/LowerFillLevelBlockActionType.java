package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Takes one level from the block: a cauldron's water, as a bucket or a wash would, emptying it to a plain cauldron at the
 * last level; or any block with a {@code level} property above its lowest. Fails on a block with nothing to take.
 *
 * <p>JSON: {@code { "type":"pheno:lower_fill_level" }}</p>
 */
public final class LowerFillLevelBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:lower_fill_level";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        return ctx -> {
            var level = ctx.level();
            var pos = ctx.pos();
            if (level == null || pos == null) {
                ctx.fail();
                return;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LayeredCauldronBlock) {
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                return;
            }
            for (Property<?> property : state.getProperties()) {
                if (!property.getName().equals("level") || !(property instanceof IntegerProperty levels)) continue;
                int now = state.getValue(levels);
                int lowest = levels.getPossibleValues().stream().mapToInt(Integer::intValue).min().orElse(0);
                if (now <= lowest) break;
                level.setBlock(pos, state.setValue(levels, now - 1), 3);
                return;
            }
            ctx.fail();
        };
    }
}
