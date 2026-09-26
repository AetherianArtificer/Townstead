package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.politics.standing.Standing;
import com.aetherianartificer.townstead.politics.standing.StandingService;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;

/**
 * {@code pheno:standing}: how much the entity in focus counts in the town it stands in. With
 * {@code source} set to {@code hearts}, {@code deeds}, or {@code reputation}, one part of it.
 * Zero outside any town range.
 */
public final class StandingValueType implements ValueType {

    public static final String KEY = "pheno:standing";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        String source = GsonHelper.getAsString(json, "source", "total");
        if (!source.equals("total") && !source.equals("hearts") && !source.equals("deeds") && !source.equals("reputation")) {
            return null;
        }
        return ctx -> {
            LivingEntity self = ctx.self();
            if (self == null || !(self.level() instanceof ServerLevel level)) return 0;
            return TownRange.at(level, self.blockPosition()).map(village -> {
                Standing standing = StandingService.of(level.getServer(), self.getUUID(),
                        new SettlementRef(level.dimension().location(), village.getId()));
                return (double) switch (source) {
                    case "hearts" -> standing.hearts();
                    case "deeds" -> standing.deeds();
                    case "reputation" -> standing.reputation();
                    default -> standing.total();
                };
            }).orElse(0.0);
        };
    }
}
