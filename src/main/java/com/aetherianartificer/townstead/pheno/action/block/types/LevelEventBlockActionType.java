package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** Emits a vanilla level event; {@code data:"block"} supplies the focused block-state id. */
public final class LevelEventBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:level_event";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        int event = GsonHelper.getAsInt(json, "event", -1);
        String dataSource = GsonHelper.getAsString(json, "data", "0");
        int literal;
        try { literal = Integer.parseInt(dataSource); }
        catch (NumberFormatException ignored) { literal = 0; }
        if (event < 0 || (!"block".equals(dataSource) && !dataSource.matches("-?\\d+"))) return null;
        int data = literal;
        return context -> context.level().levelEvent(event, context.pos(), "block".equals(dataSource)
                ? net.minecraft.world.level.block.Block.getId(context.level().getBlockState(context.pos()))
                : data);
    }
}
