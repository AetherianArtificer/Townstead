package com.aetherianartificer.townstead.pheno.seek;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mobs on their way somewhere because a {@code pheno:seek} sent them: to the nearest creature that passes a condition,
 * or the nearest block that does, and then an action when they get there. A round visits each one in turn. A seek runs
 * only while the mob is doing what its {@code when} names (a villager's {@code work}, {@code idle} or {@code meet},
 * any schedule activity by id, or {@code any}); a mob with no schedule counts as {@code idle} while it has nothing to
 * fight. Several seeks may wait on one mob; the first whose time it is goes. Brain-driven mobs walk by their own walk
 * target, others by their navigation. Server memory only: a restart forgets where anyone was going.
 */
public final class Seeks {

    /** One seek as its JSON says it. */
    public record Order(ResourceLocation id, @Nullable Condition entities, @Nullable BlockCondition blocks,
                        Within within, boolean each, int limit, Set<String> when, double speed, double reach, int giveUp,
                        int revisit, int lasts, @Nullable Action onArrival, boolean onFound, @Nullable Action onGiveUp,
                        com.google.gson.JsonObject source) {}

    /** Where to look: a place and its JSON, and where to look instead when that place is nowhere (no village). */
    public record Within(SeekPlaces.Place place, com.google.gson.JsonObject params, @Nullable Within otherwise) {
        public List<AABB> regions(Mob seeker, @Nullable LivingEntity origin, @Nullable BlockPos focus) {
            List<AABB> regions = place.regions(seeker, origin, focus, params);
            if (regions.isEmpty() && otherwise != null) return otherwise.regions(seeker, origin, focus);
            return regions;
        }
    }

    private static final class Running {
        final Order order;
        final WeakReference<Mob> mob;
        final WeakReference<LivingEntity> origin;
        final BlockPos focus;
        final long endsAt;
        double closest = Double.MAX_VALUE;
        WeakReference<LivingEntity> target;
        BlockPos targetPos;
        long targetSince, nextLook, lastWalk;
        int visits;

        Running(Order order, Mob mob, @Nullable LivingEntity origin, @Nullable BlockPos focus, long now) {
            this.order = order;
            this.mob = new WeakReference<>(mob);
            this.origin = new WeakReference<>(origin);
            this.focus = focus == null ? null : focus.immutable();
            this.endsAt = now + order.lasts();
        }

        boolean hasTarget() {
            return targetPos != null || (target != null && target.get() != null);
        }

        void clearTarget() {
            target = null;
            targetPos = null;
        }
    }

    private static final int EVERY = 5;
    private static final Map<UUID, Map<ResourceLocation, Running>> RUNNING = new ConcurrentHashMap<>();
    /** Who or where each seek visited, and when, so a round passes over them until {@code revisit} ticks go by. */
    private static final Map<UUID, Map<ResourceLocation, Map<Object, Long>>> VISITED = new ConcurrentHashMap<>();

    /** Seeks started or called off while the seeks were stepping, as an arrival's action does: applied after. */
    private static final List<Runnable> LATER = new ArrayList<>();
    private static boolean stepping;

    private Seeks() {}

    /** Sends {@code mob} on {@code order}; a seek with the same id starts over. False if it cannot walk. */
    public static boolean start(LivingEntity entity, Order order) {
        return start(entity, order, null, null);
    }

    /** As {@link #start(LivingEntity, Order)}, remembering who started it and the block the action was about. */
    public static boolean start(LivingEntity entity, Order order, @Nullable LivingEntity origin, @Nullable BlockPos focus) {
        if (!(entity instanceof Mob mob) || entity.level().isClientSide() || !entity.isAlive()) return false;
        if (stepping) {
            LATER.add(() -> start(entity, order, origin, focus));
            return true;
        }
        RUNNING.computeIfAbsent(mob.getUUID(), key -> new LinkedHashMap<>())
                .put(order.id(), new Running(overridden(order), mob, origin, focus, mob.level().getGameTime()));
        return true;
    }

    /** Calls off a seek, or every seek when {@code id} is null. */
    public static void stop(LivingEntity entity, @Nullable ResourceLocation id) {
        if (stepping) {
            LATER.add(() -> stop(entity, id));
            return;
        }
        Map<ResourceLocation, Running> seeks = RUNNING.get(entity.getUUID());
        if (seeks == null) return;
        if (id == null) seeks.clear();
        else seeks.remove(id);
        if (seeks.isEmpty()) RUNNING.remove(entity.getUUID());
        if (entity instanceof Mob mob) mob.getNavigation().stop();
    }

    /** Whether {@code entity} is on that seek, or on any when {@code id} is null. */
    public static boolean seeking(LivingEntity entity, @Nullable ResourceLocation id) {
        Map<ResourceLocation, Running> seeks = RUNNING.get(entity.getUUID());
        if (seeks == null || seeks.isEmpty()) return false;
        return id == null || seeks.containsKey(id);
    }

    public static void tick(MinecraftServer server) {
        if (RUNNING.isEmpty() || server.getTickCount() % EVERY != 0) return;
        stepping = true;
        try {
            stepAll();
        } finally {
            stepping = false;
        }
        List<Runnable> later = new ArrayList<>(LATER);
        LATER.clear();
        later.forEach(Runnable::run);
    }

    private static void stepAll() {
        Iterator<Map.Entry<UUID, Map<ResourceLocation, Running>>> mobs = RUNNING.entrySet().iterator();
        while (mobs.hasNext()) {
            Map<ResourceLocation, Running> seeks = mobs.next().getValue();
            boolean moved = false;
            Iterator<Running> it = seeks.values().iterator();
            while (it.hasNext()) {
                Running run = it.next();
                Mob mob = run.mob.get();
                if (mob == null || !mob.isAlive() || mob.isRemoved()) {
                    it.remove();
                    continue;
                }
                long now = mob.level().getGameTime();
                if (now >= run.endsAt) {
                    it.remove();
                    continue;
                }
                if (moved || !allowed(mob, run.order.when())) {
                    // waiting for its time: the clock on this target stops too
                    run.targetSince += EVERY;
                    continue;
                }
                if (!step(mob, run, now)) it.remove();
                else moved = true;
            }
            if (seeks.isEmpty()) mobs.remove();
        }
    }

    /** One step of a running seek; false when it is done. */
    private static boolean step(Mob mob, Running run, long now) {
        Order order = run.order;
        if (run.hasTarget() && !stillGood(mob, run)) run.clearTarget();
        if (!run.hasTarget()) {
            if (now < run.nextLook) return true;
            if (!find(mob, run, now)) {
                if (order.each() && run.visits > 0) return false;
                run.nextLook = now + 40;
                return true;
            }
            run.targetSince = now;
            run.lastWalk = 0;
            run.closest = Double.MAX_VALUE;
        }
        LivingEntity target = run.target == null ? null : run.target.get();
        Vec3 there = target != null ? target.position() : Vec3.atBottomCenterOf(run.targetPos);
        if (mob.position().distanceTo(there) <= order.reach() + (target != null ? target.getBbWidth() / 2 : 0.5)) {
            arrive(mob, run, target, now);
            run.clearTarget();
            stopWalking(mob);
            return order.each() && run.visits < order.limit();
        }
        double distance = mob.position().distanceTo(there);
        if (distance < run.closest - 1) {
            // getting closer: a long trip is not a lost one
            run.closest = distance;
            run.targetSince = now;
        }
        if (now - run.targetSince > order.giveUp()) {
            remember(mob, order, key(run), now);
            if (order.onGiveUp() != null) order.onGiveUp().run(new ActionContext(mob, target, mob).withFocusBlock(run.targetPos));
            run.clearTarget();
            stopWalking(mob);
            return true;
        }
        if (now - run.lastWalk >= 20) {
            walk(mob, target, run.targetPos, there, order.speed(), order.reach());
            run.lastWalk = now;
        }
        return true;
    }

    private static void arrive(Mob mob, Running run, @Nullable LivingEntity target, long now) {
        Order order = run.order;
        remember(mob, order, key(run), now);
        run.visits++;
        if (order.onArrival() == null) return;
        ActionContext ctx;
        if (target != null) ctx = order.onFound() ? new ActionContext(target, mob, mob) : new ActionContext(mob, target, mob);
        else ctx = new ActionContext(mob, null, mob).withFocusBlock(run.targetPos);
        order.onArrival().run(ctx);
    }

    private static boolean stillGood(Mob mob, Running run) {
        if (run.targetPos != null) return run.order.blocks() == null || run.order.blocks().test(mob.level(), run.targetPos);
        LivingEntity target = run.target.get();
        if (target == null || !target.isAlive() || target.isRemoved() || target.level() != mob.level()) return false;
        return run.order.entities() == null || run.order.entities().test(new ConditionContext(target));
    }

    private static boolean find(Mob mob, Running run, long now) {
        Order order = run.order;
        Map<Object, Long> seen = visited(mob, order, now);
        if (order.entities() != null) {
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (AABB box : regions(mob, run))
            for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != mob && e.isAlive() && !seen.containsKey(e.getUUID()))) {
                double d = e.distanceToSqr(mob);
                if (d >= bestDistance) continue;
                if (!order.entities().test(new ConditionContext(e))) continue;
                best = e;
                bestDistance = d;
            }
            if (best == null) return false;
            run.target = new WeakReference<>(best);
            return true;
        }
        if (order.blocks() != null) {
            BlockPos best = null;
            double bestDistance = Double.MAX_VALUE;
            for (AABB box : regions(mob, run))
            for (BlockPos pos : blocksIn(box)) {
                double d = pos.distSqr(mob.blockPosition());
                if (d >= bestDistance || seen.containsKey(pos.asLong())) continue;
                if (!order.blocks().test(mob.level(), pos)) continue;
                best = pos.immutable();
                bestDistance = d;
            }
            if (best == null) return false;
            run.targetPos = best;
            return true;
        }
        return false;
    }

    /** The regions of the seek's place, each kept to 512 blocks across. */
    private static List<AABB> regions(Mob mob, Running run) {
        List<AABB> out = new ArrayList<>();
        for (AABB box : run.order.within().regions(mob, run.origin.get(), run.focus)) {
            Vec3 c = box.getCenter();
            double hx = Math.min(box.getXsize() / 2, 256), hy = Math.min(box.getYsize() / 2, 64), hz = Math.min(box.getZsize() / 2, 256);
            out.add(new AABB(c.x - hx, c.y - hy, c.z - hz, c.x + hx, c.y + hy, c.z + hz));
        }
        return out;
    }

    /** The blocks of a region to search, at most 16 blocks out from its middle (8 up and down). */
    private static Iterable<BlockPos> blocksIn(AABB box) {
        Vec3 c = box.getCenter();
        int rx = (int) Math.min(Math.ceil(box.getXsize() / 2), 16), ry = (int) Math.min(Math.ceil(box.getYsize() / 2), 8),
                rz = (int) Math.min(Math.ceil(box.getZsize() / 2), 16);
        BlockPos center = BlockPos.containing(c);
        return BlockPos.betweenClosed(center.offset(-rx, -ry, -rz), center.offset(rx, ry, rz));
    }

    /** The order as {@code config/pheno/seeks.json} says it should be instead, if it says anything. */
    private static Order overridden(Order order) {
        com.google.gson.JsonObject json = SeekOverrides.apply(order.id(), order.source());
        if (json == null) return order;
        Order changed = com.aetherianartificer.townstead.pheno.action.types.SeekActionType.order(json, order.id());
        return changed == null ? order : changed;
    }

    private static Object key(Running run) {
        if (run.targetPos != null) return run.targetPos.asLong();
        LivingEntity target = run.target == null ? null : run.target.get();
        return target == null ? "none" : target.getUUID();
    }

    private static Map<Object, Long> visited(Mob mob, Order order, long now) {
        Map<Object, Long> seen = VISITED.computeIfAbsent(mob.getUUID(), key -> new ConcurrentHashMap<>())
                .computeIfAbsent(order.id(), key -> new ConcurrentHashMap<>());
        seen.values().removeIf(at -> now - at >= order.revisit());
        return seen;
    }

    private static void remember(Mob mob, Order order, Object key, long now) {
        if (order.revisit() > 0) visited(mob, order, now).put(key, now);
    }

    /** Paths reach about this far; a farther target is walked to in stages. */
    private static final double STAGE = 32;

    private static void walk(Mob mob, @Nullable LivingEntity target, @Nullable BlockPos pos, Vec3 there, double speed, double reach) {
        Vec3 from = mob.position();
        if (from.distanceTo(there) > STAGE + 8) {
            Vec3 step = from.add(there.subtract(from).normalize().scale(STAGE));
            int x = net.minecraft.util.Mth.floor(step.x), z = net.minecraft.util.Mth.floor(step.z);
            int y = mob.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            target = null;
            pos = new BlockPos(x, y, z);
            reach = 2;
        }
        Brain<?> brain = mob.getBrain();
        int close = Math.max(0, (int) Math.floor(reach));
        if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            if (target != null) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), (float) speed, close));
                if (brain.checkMemory(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED)) {
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                }
            } else {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(pos), (float) speed, close));
            }
            return;
        }
        if (target != null) mob.getNavigation().moveTo(target, speed);
        else mob.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, speed);
    }

    private static void stopWalking(Mob mob) {
        if (mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED)) {
            mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        mob.getNavigation().stop();
    }

    /** Whether it is this seek's time: never asleep or carried; otherwise as {@code when} names the mob's activity. */
    private static boolean allowed(Mob mob, Set<String> when) {
        if (mob.isSleeping() || mob.isPassenger() || mob.isLeashed()) return false;
        if (when.contains("any")) return true;
        java.util.Optional<Activity> activity = mob.getBrain().getActiveNonCoreActivity();
        if (activity.isPresent()) {
            ResourceLocation key = BuiltInRegistries.ACTIVITY.getKey(activity.get());
            if (key == null) return false;
            return when.contains(key.toString()) || ("minecraft".equals(key.getNamespace()) && when.contains(key.getPath()));
        }
        return when.contains("idle") && mob.getTarget() == null;
    }

    public static void clearAll() {
        RUNNING.clear();
        VISITED.clear();
    }

    /** For a debug view: who is seeking what. */
    public static List<String> describe(LivingEntity entity) {
        Map<ResourceLocation, Running> seeks = RUNNING.get(entity.getUUID());
        List<String> out = new ArrayList<>();
        if (seeks != null) for (Running run : seeks.values()) out.add(run.order.id() + " visits=" + run.visits);
        return out;
    }
}
