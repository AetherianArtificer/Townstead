package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.data.ScalarData;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Sets, adds to, or removes one scalar persistent-data key on a block entity. */
public final class ChangeBlockDataBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:change_block_data";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        String key = GsonHelper.getAsString(json, "key", "");
        String operation = GsonHelper.getAsString(json, "operation", "set").toLowerCase(java.util.Locale.ROOT);
        JsonElement literal = ScalarData.scalar(json.get("value"));
        Value computed = literal == null && json.has("value") ? Values.parse(json.get("value")) : null;
        double min = GsonHelper.getAsDouble(json, "min", -Double.MAX_VALUE);
        double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
        if (key.isBlank() || !java.util.List.of("set", "add", "remove").contains(operation)
                || ("add".equals(operation) && computed == null
                    && (literal == null || !literal.getAsJsonPrimitive().isNumber()))
                || (!"remove".equals(operation) && literal == null && computed == null)) return null;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) {
                return context.level().getBlockEntity(context.pos()) != null;
            }

            @Override public void run(BlockActionContext context) {
                var entity = context.level().getBlockEntity(context.pos());
                if (entity == null) { context.fail(); return; }
                var tag = entity.getPersistentData();
                if ("remove".equals(operation)) {
                    tag.remove(key);
                } else {
                    double number = computed == null ? (literal != null && literal.getAsJsonPrimitive().isNumber()
                            ? literal.getAsDouble() : 0)
                            : computed.get(SelectorContext.ofBlock(context.level(), context.pos(), context.cause()));
                    if ("add".equals(operation)) {
                        tag.putDouble(key, Math.max(min, Math.min(max, tag.getDouble(key) + number)));
                    } else {
                        ScalarData.put(tag, key, literal, number);
                    }
                }
                entity.setChanged();
                var state = context.level().getBlockState(context.pos());
                context.level().sendBlockUpdated(context.pos(), state, state, 3);
            }
        };
    }
}
