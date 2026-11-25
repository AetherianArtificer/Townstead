package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.api.v1.model.VillageId;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.village.ResidentRegister;
import com.aetherianartificer.townstead.village.TownRange;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * {@code pheno:village_needs}: how well the town at the focus is provided for, from -1 (every
 * need in crisis) to 1 (every need thriving). Zero outside a town or before any reading exists.
 * With {@code need}, only that need: crisis -1, strained -0.5, steady 0.5, thriving 1.
 * <pre>{ "type": "pheno:village_needs", "need": "hunger" }</pre>
 */
public final class VillageNeedsValueType implements ValueType {

    public static final String KEY = "pheno:village_needs";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        String need = json.has("need") ? json.get("need").getAsString().toLowerCase(java.util.Locale.ROOT) : null;
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return 0;
            return villageFor(ctx, level).flatMap(village -> ResidentRegister.get(level.getServer())
                    .summary(level.getServer(), new VillageId(level.dimension().location(), village.getId())))
                    .map(summary -> {
                        if (need != null) {
                            var stat = summary.byNeed().get(need);
                            return stat == null ? 0.0 : score(stat.band());
                        }
                        if (summary.byNeed().isEmpty()) return 0.0;
                        double total = 0;
                        for (var stat : summary.byNeed().values()) total += score(stat.band());
                        return total / summary.byNeed().size();
                    }).orElse(0.0);
        };
    }

    /**
     * The town being read: one named on the context, else the town the focus stands in, else the
     * home town of the villager reading it. A villager working fields past the border still
     * answers for their own town instead of reading as nowhere.
     */
    private static java.util.Optional<Village> villageFor(SelectorContext ctx, ServerLevel level) {
        VillageManager manager = VillageManager.get(level);
        Integer named = ctx.villageId();
        if (named != null) {
            java.util.Optional<Village> village = manager.getOrEmpty(named);
            if (village.isPresent()) return village;
        }
        java.util.Optional<Village> here = TownRange.at(level, BlockPos.containing(ctx.pos()));
        if (here.isPresent()) return here;
        if (ctx.self() instanceof VillagerEntityMCA villager && villager.level() == level) {
            try {
                return villager.getResidency().getHomeVillage();
            } catch (RuntimeException e) {
                return java.util.Optional.empty();
            }
        }
        return java.util.Optional.empty();
    }

    private static double score(com.aetherianartificer.townstead.api.v1.model.NeedBand band) {
        return switch (band) {
            case THRIVING -> 1.0;
            case STEADY -> 0.5;
            case STRAINED -> -0.5;
            case CRISIS -> -1.0;
        };
    }
}
