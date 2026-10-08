package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.shift.WorkWeek;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

/**
 * {@code pheno:work_hours}: work hours in the focus villager's schedule. {@code of} picks
 * {@code longest_day} (default), {@code shortest_day} or {@code week}. Zero for anyone who is not
 * a villager.
 * <pre>{ "type": "pheno:work_hours", "of": "longest_day" }</pre>
 */
public final class WorkHoursValueType implements ValueType {
    public static final String KEY = "pheno:work_hours";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Value parse(JsonObject json) {
        String of = GsonHelper.getAsString(json, "of", "longest_day");
        if (!of.equals("longest_day") && !of.equals("shortest_day") && !of.equals("week")) return null;
        return ctx -> {
            if (!(ctx.self() instanceof VillagerEntityMCA villager) || !(villager.level() instanceof ServerLevel level)) return 0;
            var hours = WorkWeek.days(level.getServer(), villager).stream().mapToInt(WorkWeek::workHours);
            return switch (of) {
                case "shortest_day" -> hours.min().orElse(0);
                case "week" -> hours.sum();
                default -> hours.max().orElse(0);
            };
        };
    }
}
