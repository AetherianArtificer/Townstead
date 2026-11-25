package com.aetherianartificer.townstead.habit;

import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

/**
 * True when a villager is due for a habit by their personality's leaning ({@code habit} data), or is in the middle of a
 * round of it: {@code { "type": "pheno:habit_due", "habit": "cleaning" }}. Gates a job to the times its worker wants to do it.
 */
public final class HabitDueConditionType implements ConditionType {
    public static final String KEY = "pheno:habit_due";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Condition parse(JsonObject json) {
        String habit = GsonHelper.getAsString(json, "habit", "");
        if (!Habits.validName(habit)) return null;
        return ctx -> ctx.entity() instanceof VillagerEntityMCA villager
                && Habits.due(villager, habit, villager.level().getGameTime());
    }
}
