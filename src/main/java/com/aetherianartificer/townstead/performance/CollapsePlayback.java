package com.aetherianartificer.townstead.performance;

import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.villager.TownsteadVillagers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/** Server-owned collapse movement; navigation never competes with the authored steps. */
public final class CollapsePlayback {
    private static final int LEASE = 1200;
    private static final Map<VillagerEntityMCA, State> ACTIVE = new WeakHashMap<>();
    private static final class State {
        long start;
        long expires;
        long lastTick;
        float yaw;
        String channel;
        int priority;
        boolean fatigue;
    }
    private CollapsePlayback() {}

    public static PerformanceHandle start(VillagerEntityMCA actor, String channel, int duration, int priority) {
        stop(actor);
        State s = new State();
        s.start = s.lastTick = actor.level().getGameTime();
        s.expires = s.start + duration;
        s.yaw = actor.yBodyRot;
        s.channel = channel;
        s.priority = priority;
        ACTIVE.put(actor, s);
        actor.stopRiding();
        freeze(actor, s);
        broadcast(actor, packet(actor, s));
        return () -> { if (ACTIVE.get(actor) == s) stop(actor); };
    }

    public static void startFatigue(VillagerEntityMCA actor) {
        start(actor, CollapseMotion.CHANNEL, LEASE, 1000);
        ACTIVE.get(actor).fatigue = true;
    }

    /** Loaded unconscious villagers resume the hold, never repeat or reapply the fall. */
    private static State restore(VillagerEntityMCA actor) {
        State s = new State();
        s.lastTick = actor.level().getGameTime();
        s.start = Math.max(0, s.lastTick - CollapseMotion.DURATION);
        s.expires = s.lastTick + LEASE;
        s.yaw = actor.getYRot(); // Entity yaw survives reload; the transient body yaw may not.
        s.channel = CollapseMotion.CHANNEL;
        s.priority = 1000;
        s.fatigue = true;
        ACTIVE.put(actor, s);
        return s;
    }

    public static boolean active(VillagerEntityMCA actor) { return ACTIVE.containsKey(actor); }

    public static void beforeAi(VillagerEntityMCA actor) {
        State s = ACTIVE.get(actor);
        if (s != null) {
            actor.getBrain().stopAll((net.minecraft.server.level.ServerLevel) actor.level(), actor);
            freeze(actor, s);
        }
    }

    /** Runs after all other server tick behaviors, including reaction locks. */
    public static void tick(VillagerEntityMCA actor) {
        if (!actor.isAlive() || actor.isRemoved() || actor.isSleeping()) {
            stop(actor);
            return;
        }
        boolean collapsed = TownsteadConfig.isVillagerFatigueEnabled()
                && TownsteadVillagers.get(actor).needs().collapsed();
        State s = ACTIVE.get(actor);
        long now = actor.level().getGameTime();
        if (s == null && collapsed) {
            s = restore(actor);
            broadcast(actor, packet(actor, s));
        }
        if (s == null) return;
        if (s.fatigue ? !collapsed : now >= s.expires) {
            stop(actor);
            return;
        }
        freeze(actor, s);
        if (actor.isPassenger()) actor.stopRiding();
        // Advance from the attempted keyframe, not the actual distance travelled:
        // a blocked step is discarded instead of accumulating a later teleport.
        Vec3 delta = CollapseMotion.step(Math.max(s.lastTick, now - 1) - s.start, now - s.start, s.yaw);
        var destination = actor.getBoundingBox().move(delta);
        var support = new net.minecraft.world.phys.AABB(destination.minX, destination.minY - .5,
                destination.minZ, destination.maxX, destination.minY - .001, destination.maxZ);
        if (actor.onGround() && delta.lengthSqr() > 0
                && actor.level().getBlockCollisions(actor, support).iterator().hasNext()) {
            actor.move(MoverType.SELF, delta);
        }
        s.lastTick = now;
        if (now - s.start >= 82 && now % 16 == Math.floorMod(actor.getId(), 16)) {
            double facing = Math.toRadians(s.yaw);
            ((net.minecraft.server.level.ServerLevel) actor.level()).sendParticles(
                    com.aetherianartificer.townstead.fatigue.SleepParticles.SLEEP.get(),
                    actor.getX() - Math.sin(facing) * .65, actor.getY() + .55,
                    actor.getZ() + Math.cos(facing) * .65, 1, .08, .03, .08, 0);
        }
        if (s.fatigue && s.expires - now < LEASE / 2) {
            s.expires = now + LEASE;
            broadcast(actor, packet(actor, s));
        }
    }

    public static void syncToWatcher(ServerPlayer watcher, VillagerEntityMCA actor) {
        State s = ACTIVE.get(actor);
        if (s == null && !actor.isSleeping() && TownsteadConfig.isVillagerFatigueEnabled()
                && TownsteadVillagers.get(actor).needs().collapsed()) s = restore(actor);
        if (s == null) return;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(watcher, packet(actor, s));
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(watcher, packet(actor, s));
        *///?}
    }

    public static void stop(VillagerEntityMCA actor) {
        State s = ACTIVE.remove(actor);
        if (s != null) broadcast(actor, new NativePerformanceS2CPayload(actor.getId(), s.channel, "", 0, s.priority));
    }

    private static NativePerformanceS2CPayload packet(VillagerEntityMCA actor, State s) {
        return new NativePerformanceS2CPayload(actor.getId(), s.channel, CollapseMotion.CLIP,
                (int) Math.max(1, s.expires - actor.level().getGameTime()), s.priority, s.start);
    }

    private static void freeze(VillagerEntityMCA actor, State s) {
        actor.getNavigation().stop();
        actor.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        actor.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        actor.setDeltaMovement(0, actor.getDeltaMovement().y, 0);
        actor.setYRot(s.yaw);
        actor.yBodyRot = s.yaw;
        actor.yHeadRot = s.yaw;
    }

    private static void broadcast(VillagerEntityMCA actor, NativePerformanceS2CPayload packet) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(actor, packet);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(actor, packet);
        *///?}
    }
}
