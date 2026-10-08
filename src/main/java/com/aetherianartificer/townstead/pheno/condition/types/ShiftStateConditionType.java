package com.aetherianartificer.townstead.pheno.condition.types;

import com.aetherianartificer.townstead.clothing.ClothingQuery;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.aetherianartificer.townstead.shift.ShiftData;
import com.aetherianartificer.townstead.shift.VillagerSchedules;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Where the villager is in their working week: {@code on_shift} while the schedule says work,
 * {@code day_off} on a day whose schedule holds no work at all, {@code off_shift} otherwise.
 *
 * <p>JSON: {@code { "type":"pheno:shift_state", "state": ["off_shift", "day_off"] }}. Players
 * and other entities have no shift and match nothing.</p>
 */
public final class ShiftStateConditionType implements ConditionType {

    public static final String KEY = "pheno:shift_state";

    public enum State { ON_SHIFT, OFF_SHIFT, DAY_OFF }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        Set<State> wanted = new LinkedHashSet<>();
        for (String raw : ClothingQuery.strings(json.get("state"))) {
            try {
                wanted.add(State.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // An unknown state name is a typo the author sees as a policy that never fires.
            }
        }
        return ctx -> ctx.entity() instanceof VillagerEntityMCA villager && wanted.contains(stateOf(villager));
    }

    public static State stateOf(VillagerEntityMCA villager) {
        if (villager == null || villager.getBrain() == null) return State.OFF_SHIFT;
        if (VillagerSchedules.currentActivity(villager) == Activity.WORK) return State.ON_SHIFT;
        Schedule schedule = villager.getBrain().getSchedule();
        if (schedule == null) return State.OFF_SHIFT;
        for (int hour = 0; hour < ShiftData.HOURS_PER_DAY; hour++) {
            if (schedule.getActivityAt(hour * ShiftData.TICKS_PER_HOUR) == Activity.WORK) return State.OFF_SHIFT;
        }
        return State.DAY_OFF;
    }
}
