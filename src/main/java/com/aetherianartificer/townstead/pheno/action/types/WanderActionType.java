package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.Vec3;

/**
 * Starts one bounded, pathfinder-backed wander only while the actor is genuinely at leisure.
 * It never teleports, never interrupts an existing path, and yields to work, rest, panic, combat,
 * riding, and sleep. This makes temporary state flavor safe to author without a custom AI goal.
 */
public final class WanderActionType implements ActionType {
    public static final String KEY = "pheno:wander";

    @Override public String key() { return KEY; }

    @Override
    public Action parse(JsonObject json) {
        int horizontal = Math.max(1, Math.min(12, GsonHelper.getAsInt(json, "horizontal", 3)));
        int vertical = Math.max(1, Math.min(6, GsonHelper.getAsInt(json, "vertical", 2)));
        double speed = Math.max(0.1D, Math.min(1.0D, GsonHelper.getAsDouble(json, "speed", 0.4D)));
        return ctx -> {
            if (!(ctx.entity() instanceof Mob mob) || mob.isSleeping() || mob.isPassenger()
                    || mob.isVehicle() || mob.getTarget() != null || mob.getLastHurtByMob() != null
                    || !mob.getNavigation().isDone()) return;
            if (mob.getBrain().isActive(Activity.PANIC) || mob.getBrain().isActive(Activity.WORK)
                    || mob.getBrain().isActive(Activity.REST)) return;
            Activity scheduled = mob.getBrain().getSchedule().getActivityAt(
                    (int) (mob.level().getDayTime() % 24000L));
            if (scheduled == Activity.WORK || scheduled == Activity.REST) return;
            if (!(mob instanceof PathfinderMob pathfinder)) return;
            Vec3 destination = DefaultRandomPos.getPos(pathfinder, horizontal, vertical);
            if (destination != null) {
                mob.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
            }
        };
    }
}
