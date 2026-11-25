package com.aetherianartificer.townstead.habit;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.util.GsonHelper;

/**
 * Counts a go at a habit for the villager doing a job, when the actions before it in the list succeeded:
 * {@code { "type": "pheno:habit_done", "habit": "cleaning" }}. Put it last, after what does the work. With
 * {@code "round": true} it counts the whole round done at once, for a habit done in one go, such as a wash of clothes.
 */
public final class HabitDoneBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:habit_done";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        String habit = GsonHelper.getAsString(json, "habit", "");
        if (!Habits.validName(habit)) return null;
        boolean round = GsonHelper.getAsBoolean(json, "round", false);
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) {
                return true;
            }

            @Override public void run(BlockActionContext context) {
                if (context.succeeded() && context.cause() instanceof VillagerEntityMCA villager) {
                    if (round) Habits.finished(villager, habit, context.level().getGameTime());
                    else Habits.did(villager, habit, context.level().getGameTime());
                }
            }
        };
    }
}
