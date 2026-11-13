package com.aetherianartificer.townstead.pheno.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Mobs made to flee by {@code pheno:flee}: until the time runs out the mob drops its target and
 * walks away from the entity that scared it. Transient combat state, not saved.
 */
public final class Fears {

    private record Fear(WeakReference<LivingEntity> source, long until, double speed) {}

    private static final Map<Mob, Fear> FEARS = new WeakHashMap<>();
    private static final int STEER_EVERY = 10;
    private static final double RUN_DISTANCE = 10.0;

    private Fears() {}

    public static void scare(Mob mob, LivingEntity source, int ticks, double speed) {
        if (mob.level().isClientSide || mob == source || ticks <= 0) return;
        FEARS.put(mob, new Fear(new WeakReference<>(source), mob.level().getGameTime() + ticks, speed));
        steer(mob, source, speed);
    }

    public static boolean afraid(LivingEntity entity) {
        return entity instanceof Mob mob && FEARS.containsKey(mob);
    }

    public static void tick(LivingEntity entity) {
        if (FEARS.isEmpty() || !(entity instanceof Mob mob)) return;
        Fear fear = FEARS.get(mob);
        if (fear == null) return;
        LivingEntity source = fear.source().get();
        if (source == null || !source.isAlive() || mob.level().getGameTime() >= fear.until()) {
            FEARS.remove(mob);
            return;
        }
        if (mob.getTarget() != null) mob.setTarget(null);
        if (mob.tickCount % STEER_EVERY == 0) steer(mob, source, fear.speed());
    }

    private static void steer(Mob mob, LivingEntity source, double speed) {
        mob.setTarget(null);
        // Speed 0 is a truce: the mob drops its target and stays where it is.
        if (speed <= 0) {
            mob.getNavigation().stop();
            return;
        }
        Vec3 away = mob.position().subtract(source.position());
        if (away.lengthSqr() < 1.0e-4) away = new Vec3(1, 0, 0);
        away = away.normalize().scale(RUN_DISTANCE);
        mob.getNavigation().moveTo(mob.getX() + away.x, mob.getY(), mob.getZ() + away.z, speed);
    }

    public static void clearAll() {
        FEARS.clear();
    }
}
