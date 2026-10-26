package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.Optional;

/**
 * True when the villager in focus has its home in the village it stands in. Visitors, animals,
 * golems and players are not residents. With {@code anywhere}, a home in any village counts.
 * <pre>{ "type": "pheno:resident" }</pre>
 */
public final class ResidentConditionType implements ConditionType {
    public static final String KEY = "pheno:resident";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        boolean anywhere = GsonHelper.getAsBoolean(json, "anywhere", false);
        return ctx -> {
            if (!(ctx.entity() instanceof VillagerEntityMCA villager) || !(ctx.level() instanceof ServerLevel level)) return false;
            Optional<Village> home;
            try {
                home = villager.getResidency().getHomeVillage();
            } catch (RuntimeException e) {
                return false;
            }
            if (home.isEmpty()) return false;
            if (anywhere) return true;
            return VillageManager.get(level).findNearestVillage(ctx.pos(), Village.MERGE_MARGIN)
                    .map(here -> here.getId() == home.get().getId()).orElse(false);
        };
    }
}
