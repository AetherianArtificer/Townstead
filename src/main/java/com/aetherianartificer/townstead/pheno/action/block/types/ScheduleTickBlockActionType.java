package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Schedules a block tick for the target block after {@code delay} ticks (Apugli's
 * {@code schedule_tick}), e.g. to make a block re-evaluate its state. No-op on air.
 *
 * <p>JSON: {@code { "type":"pheno:schedule_tick", "delay":1 }}</p>
 */
public final class ScheduleTickBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:schedule_tick";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        int delay = GsonHelper.getAsInt(json, "delay", 1);
        return ctx -> {
            BlockState state = ctx.level().getBlockState(ctx.pos());
            if (!state.isAir()) ctx.level().scheduleTick(ctx.pos(), state.getBlock(), delay);
        };
    }
}
