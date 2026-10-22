package com.aetherianartificer.townstead.root.locomotion;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Movement modes layered over MCA's navigation. The brain's {@code WALK_TARGET} stays the
 * one source of where a mob wants to go; a mode only changes how it gets there.
 *
 * <p>Off the ground, {@code MobLocomotionMixin} skips the move-control and jump-control
 * ticks, and the navigation tick too while the {@link Pilot} owns navigation. The
 * navigation path is kept, not stopped: {@code MoveToTargetSink} treats a stopped
 * navigation as a finished trip and erases the walk target. The sink can still drop the
 * target on its own timeout mid-trip, so the destination is latched here and survives
 * that gap.</p>
 *
 * <p>While a pilot flies the body, wall crashes and falls do no harm: the pilot is
 * responsible for the landing, and a misjudged one should not kill a villager.</p>
 */
public final class Locomotion {

    private Locomotion() {}

    public static final class State {
        final MovementMode mode;
        final Pilot pilot;
        final long enteredAt;
        @Nullable BlockPos destination;

        State(MovementMode mode, Pilot pilot, long enteredAt, @Nullable BlockPos destination) {
            this.mode = mode;
            this.pilot = pilot;
            this.enteredAt = enteredAt;
            this.destination = destination;
        }
    }

    public static MovementMode mode(Mob mob) {
        State state = state(mob);
        return state == null ? MovementMode.GROUND : state.mode;
    }

    @Nullable
    public static Pilot pilot(Mob mob) {
        State state = state(mob);
        return state == null ? null : state.pilot;
    }

    /** Hand the body to a pilot, replacing any current one. */
    public static void start(Mob mob, MovementMode mode, Pilot pilot, @Nullable BlockPos destination) {
        if (mode == MovementMode.GROUND) throw new IllegalArgumentException("ground has no pilot");
        State previous = state(mob);
        if (previous != null) previous.pilot.stop(mob);
        ((LocomotionCarrier) mob).townstead$setLocomotion(new State(mode, pilot, mob.level().getGameTime(),
                destination == null ? null : destination.immutable()));
        // Released controls keep their last inputs; clear them so the walk cycle doesn't drive the pilot.
        mob.setSpeed(0f);
        mob.setZza(0f);
        mob.setXxa(0f);
        mob.setJumping(false);
    }

    /** Return the body to MCA, re-planning the kept path from wherever the mob ended up. */
    public static void exit(Mob mob) {
        State state = state(mob);
        if (state == null) return;
        ((LocomotionCarrier) mob).townstead$setLocomotion(null);
        state.pilot.stop(mob);
        PathNavigation navigation = mob.getNavigation();
        if (!navigation.isDone()) navigation.recomputePath();
    }

    /** Where the mob is trying to go: the brain's walk target, else the navigation target, else the latched one. */
    @Nullable
    public static BlockPos destination(Mob mob) {
        BlockPos live = liveTarget(mob);
        if (live != null) return live;
        State state = state(mob);
        return state == null ? null : state.destination;
    }

    /** Runs the pilot after vanilla travel; drops modes the body can no longer be in. */
    public static void tick(Mob mob) {
        State state = state(mob);
        if (state == null) return;
        BlockPos live = liveTarget(mob);
        if (live != null) state.destination = live;
        if (mob.isPassenger() || mob.isSleeping() || mob.isDeadOrDying()) {
            exit(mob);
            return;
        }
        if (!state.pilot.tick(mob) && state(mob) == state) exit(mob);
    }

    /** Wall crashes and falls are the pilot's fault, not the villager's. */
    public static boolean shieldsFrom(LivingEntity entity, DamageSource source) {
        if (!(entity instanceof Mob mob) || state(mob) == null) return false;
        return source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypeTags.IS_FALL);
    }

    /** One line for debug reports. */
    public static String describe(Mob mob) {
        State state = state(mob);
        if (state == null) return "mode GROUND";
        long age = mob.level().getGameTime() - state.enteredAt;
        return "mode " + state.mode + " for " + age + "t, " + state.pilot.describe()
                + (state.destination == null ? "" : ", heading for " + state.destination.toShortString());
    }

    /** For {@code MobLocomotionMixin}: whether move control and jumping sit out this tick. */
    public static boolean suspendsBodyControl(LocomotionCarrier carrier) {
        return carrier.townstead$locomotion() != null;
    }

    /** For {@code MobLocomotionMixin}: whether the navigation tick sits out too. */
    public static boolean suspendsNavigation(LocomotionCarrier carrier) {
        State state = carrier.townstead$locomotion();
        return state != null && state.pilot.ownsNavigation();
    }

    @Nullable
    private static State state(Mob mob) {
        return ((LocomotionCarrier) mob).townstead$locomotion();
    }

    @Nullable
    private static BlockPos liveTarget(Mob mob) {
        if (mob.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            WalkTarget walk = mob.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            if (walk != null) return walk.getTarget().currentBlockPosition();
        }
        Path path = mob.getNavigation().getPath();
        return path == null ? null : path.getTarget();
    }
}
