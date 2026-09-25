package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.spirit.VillageSpiritAggregator;
import com.aetherianartificer.townstead.spirit.VillageSpiritCache;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** {@code pheno:village_spirit_tier}: the spirit tier of the town at the focus, 0 outside a town. */
public final class VillageSpiritTierValueType implements ValueType {

    public static final String KEY = "pheno:village_spirit_tier";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return 0;
            return TownRange.at(level, BlockPos.containing(ctx.pos())).map(village -> {
                VillageSpiritCache.Entry cached = VillageSpiritCache.get(level, village.getId());
                var readout = cached != null ? cached.readout()
                        : VillageSpiritAggregator.readoutFor(VillageSpiritAggregator.snapshotFor(level, village).totals());
                return (double) Math.max(0, readout.tierIndex());
            }).orElse(0.0);
        };
    }
}
