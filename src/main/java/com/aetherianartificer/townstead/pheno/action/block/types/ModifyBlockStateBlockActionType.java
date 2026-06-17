package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Changes one blockstate {@code property} of the target block (Apoli's block
 * {@code modify_block_state}): set it to {@code value}, or {@code "operation":"cycle"}
 * to advance to the next value. No-op if the block lacks the property or the value is
 * invalid.
 *
 * <p>JSON: {@code { "type":"pheno:modify_block_state", "property":"age",
 * "value":"7" }} or {@code { ..., "property":"open", "operation":"cycle" }}</p>
 */
public final class ModifyBlockStateBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:modify_block_state";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        String propName = GsonHelper.getAsString(json, "property", "");
        if (propName.isEmpty()) return null;
        boolean cycle = "cycle".equalsIgnoreCase(GsonHelper.getAsString(json, "operation", ""));
        String value = GsonHelper.getAsString(json, "value", "");
        return ctx -> {
            BlockState state = ctx.level().getBlockState(ctx.pos());
            Property<?> property = state.getBlock().getStateDefinition().getProperty(propName);
            if (property == null) return;
            BlockState updated = cycle ? state.cycle(property) : withValue(state, property, value);
            if (updated != state) ctx.level().setBlock(ctx.pos(), updated, 3);
        };
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property, String raw) {
        return property.getValue(raw).map(v -> state.setValue(property, v)).orElse(state);
    }
}
