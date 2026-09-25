package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.village.ResidentRegister;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code pheno:village_needs}: how well the town at the focus is provided for, from -1 (every
 * need in crisis) to 1 (every need thriving). Zero outside a town or before any reading exists.
 */
public final class VillageNeedsValueType implements ValueType {

    public static final String KEY = "pheno:village_needs";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return 0;
            return TownRange.at(level, BlockPos.containing(ctx.pos())).flatMap(village -> ResidentRegister.get(level.getServer())
                    .summary(level.getServer(), new VillageId(level.dimension().location(), village.getId())))
                    .map(summary -> {
                        if (summary.byNeed().isEmpty()) return 0.0;
                        double total = 0;
                        for (var stat : summary.byNeed().values()) {
                            total += switch (stat.band()) {
                                case THRIVING -> 1.0;
                                case STEADY -> 0.5;
                                case STRAINED -> -0.5;
                                case CRISIS -> -1.0;
                            };
                        }
                        return total / summary.byNeed().size();
                    }).orElse(0.0);
        };
    }
}
