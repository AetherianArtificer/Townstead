package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.shift.WorkWeek;
import com.aetherianartificer.townstead.village.TownRange;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.Optional;

/**
 * True when the villager in focus has at least one day in their weekly schedule with no work
 * hours. With {@code village}, when every working resident of the focus's village has one.
 * A villager on a daily schedule with work hours has none.
 * <pre>{ "type": "pheno:rest_day" }</pre>
 * <pre>{ "type": "pheno:rest_day", "village": true }</pre>
 */
public final class RestDayConditionType implements ConditionType {
    public static final String KEY = "pheno:rest_day";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        boolean village = GsonHelper.getAsBoolean(json, "village", false);
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level) || ctx.pos() == null) return false;
            if (!village) return ctx.entity() instanceof VillagerEntityMCA villager && hasRestDay(level.getServer(), villager);
            Optional<Village> home = TownRange.at(level, ctx.pos());
            if (home.isEmpty()) return false;
            boolean anyWorker = false;
            for (VillagerEntityMCA resident : home.get().getResidents(level)) {
                VillagerProfession profession = resident.getVillagerData().getProfession();
                if (profession == VillagerProfession.NONE || profession == VillagerProfession.NITWIT) continue;
                anyWorker = true;
                if (!hasRestDay(level.getServer(), resident)) return false;
            }
            return anyWorker;
        };
    }

    public static boolean hasRestDay(MinecraftServer server, VillagerEntityMCA villager) {
        for (int[] day : WorkWeek.days(server, villager)) if (WorkWeek.workHours(day) == 0) return true;
        return false;
    }
}
