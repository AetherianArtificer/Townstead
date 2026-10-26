package com.aetherianartificer.townstead.root.locomotion;

import com.aetherianartificer.townstead.root.locomotion.fly.PlanThrottle;
import com.aetherianartificer.townstead.root.locomotion.fly.SwimPilot;
import com.aetherianartificer.townstead.root.locomotion.plan.AirSpace;
import com.aetherianartificer.townstead.root.locomotion.plan.SwimRoutePlanner;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * When aquatic villagers take to the water. The swimming itself belongs to {@link SwimPilot}.
 *
 * <ul>
 * <li><b>Water cost</b>: MCA steers villagers around water. Swimmers get that cost
 * lowered by style, so MCA's own navigation carries them across at the surface (a
 * paddler still prefers a short walk round). Losing the gene restores MCA's cost.</li>
 * <li><b>Swims</b>: in the water, the pilot takes over for a destination under the
 * surface, for every trip of a fish, and whenever walking can't get there.</li>
 * <li><b>Dwelling</b>: a fish that breathes water spends idle time under it, swimming
 * from one resting spot to the next nearby.</li>
 * </ul>
 *
 * <p>Runs once a second per villager, staggered; the gene scan only happens for a
 * villager in the water, idle for a dwell roll, or due a water-cost refresh.</p>
 */
public final class SwimAI {

    private SwimAI() {}

    private static final int CHECK_EVERY = 20;
    private static final int COST_EVERY = 200;
    private static final int DWELL_EVERY = 100;
    private static final int DWELL_CHANCE = 2;
    private static final float MCA_WATER_COST = 16f;
    private static final double MIN_SWIM = 4.0;
    private static final int DWELL_RADIUS = 16;
    private static final int DWELL_TRIES = 16;
    private static final int REST_MIN = 100;
    private static final int REST_SPREAD = 200;
    private static final long FAILED_COOLDOWN = 200;
    private static final double FAILED_MOVE_SQR = 4.0;
    private static final float DWELL_SPEED = 0.6f;

    private record FailedSwim(BlockPos target, BlockPos from, long until) {}

    /** Server thread only; weak keys drop entries with the entity. */
    private static final Map<VillagerEntityMCA, FailedSwim> FAILED = new WeakHashMap<>();
    private static final Map<VillagerEntityMCA, Float> ADJUSTED = new WeakHashMap<>();
    /** Resting spots chosen ashore: the swim that reaches one rests there too. */
    private static final Map<VillagerEntityMCA, BlockPos> DWELL_SPOTS = new WeakHashMap<>();

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        int phase = villager.tickCount + villager.getId();
        if (phase % CHECK_EVERY != 0) return;
        boolean costDue = phase % COST_EVERY == 0;
        boolean free = Locomotion.pilot(villager) == null && !villager.isSleeping() && !villager.isPassenger();
        boolean inWater = villager.isInWater();
        boolean dwellDue = free && !inWater && phase % DWELL_EVERY == 0;
        if (!costDue && !(free && inWater) && !dwellDue) return;
        SwimProfile profile = SwimProfile.of(villager);
        if (costDue) applyWaterCost(villager, profile);
        if (profile == null || !free) return;
        if (inWater) {
            if (!maybeSwim(villager, profile) && dwells(profile) && phase % DWELL_EVERY == 0) maybeDwell(villager, profile);
        } else if (dwells(profile)) {
            maybeDwell(villager, profile);
        }
    }

    private static boolean dwells(SwimProfile profile) {
        return profile.style().dwells() && profile.breathes();
    }

    /** Swim when the destination is under water, the swimmer is a fish, or walking can't get there. */
    private static boolean maybeSwim(VillagerEntityMCA villager, SwimProfile profile) {
        BlockPos destination = Locomotion.destination(villager);
        if (destination == null) return false;
        double dx = destination.getX() + 0.5 - villager.getX(), dz = destination.getZ() + 0.5 - villager.getZ();
        double dy = destination.getY() - villager.getY();
        if (dx * dx + dz * dz + dy * dy < MIN_SWIM * MIN_SWIM) return false;
        boolean wanted = profile.style() == SwimStyle.FISH || underWater(villager, destination)
                || !TripCost.walkCanReach(villager);
        if (!wanted) return false;
        int linger = destination.equals(DWELL_SPOTS.remove(villager)) ? rest(villager) : 0;
        return swimTo(villager, profile, destination, linger);
    }

    private static boolean underWater(VillagerEntityMCA villager, BlockPos pos) {
        AirSpace space = new AirSpace(villager.level(), villager.getBbWidth(), villager.getBbHeight(), AirSpace.Medium.WATER);
        return space.passable(pos.getX(), pos.getY(), pos.getZ()) && space.depth(pos.getX(), pos.getY(), pos.getZ(), 1) > 0;
    }

    private static boolean swimTo(VillagerEntityMCA villager, SwimProfile profile, BlockPos destination, int linger) {
        long now = villager.level().getGameTime();
        FailedSwim failed = FAILED.get(villager);
        if (failed != null && now < failed.until() && failed.target().equals(destination)
                && failed.from().distSqr(villager.blockPosition()) < FAILED_MOVE_SQR) {
            return false;
        }
        if (!PlanThrottle.tryAcquire(villager.level())) return false;
        SwimRoutePlanner.Result result = SwimRoutePlanner.plan(villager, profile, destination);
        if (!result.ok()) {
            FAILED.put(villager, new FailedSwim(destination.immutable(), villager.blockPosition(), now + FAILED_COOLDOWN));
            return false;
        }
        FAILED.remove(villager);
        Locomotion.start(villager, MovementMode.WATER,
                new SwimPilot(villager, profile, result.route(), destination, linger), destination);
        return true;
    }

    /**
     * Idle and free: pick a spot under water nearby and go and rest there. Already in the
     * water it swims straight there; ashore it sets a walk target, and MCA carries it into
     * the water, where the next check takes over.
     */
    private static void maybeDwell(VillagerEntityMCA villager, SwimProfile profile) {
        if (villager.getRandom().nextInt(DWELL_CHANCE) != 0 || !idle(villager)) return;
        BlockPos spot = dwellSpot(villager, profile);
        if (spot == null) return;
        if (villager.isInWater()) {
            swimTo(villager, profile, spot, rest(villager));
        } else {
            DWELL_SPOTS.put(villager, spot);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(spot, DWELL_SPEED, 1));
        }
    }

    private static int rest(VillagerEntityMCA villager) {
        return REST_MIN + villager.getRandom().nextInt(REST_SPREAD);
    }

    /** Free time with nothing else to do: the same test conversations use. */
    private static boolean idle(VillagerEntityMCA villager) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) return false;
        Activity activity = villager.getBrain().getSchedule().getActivityAt((int) (villager.level().getDayTime() % 24000L));
        return villager.getNavigation().isDone() && (activity == Activity.IDLE || activity == Activity.MEET);
    }

    /** A random submerged cell nearby, as deep as the style likes where the water allows. */
    private static BlockPos dwellSpot(VillagerEntityMCA villager, SwimProfile profile) {
        AirSpace space = new AirSpace(villager.level(), villager.getBbWidth(), villager.getBbHeight(), AirSpace.Medium.WATER);
        var random = villager.getRandom();
        int want = Mth.ceil(profile.style().depth());
        for (int i = 0; i < DWELL_TRIES; i++) {
            int x = villager.getBlockX() + random.nextInt(DWELL_RADIUS * 2 + 1) - DWELL_RADIUS;
            int z = villager.getBlockZ() + random.nextInt(DWELL_RADIUS * 2 + 1) - DWELL_RADIUS;
            for (int y = villager.getBlockY() + 4; y > villager.getBlockY() - DWELL_RADIUS; y--) {
                if (!space.passable(x, y, z)) continue;
                if (space.depth(x, y, z, want + 1) >= Math.max(1, want)) return new BlockPos(x, y, z);
            }
        }
        return null;
    }

    /** Swimmers get MCA's water cost lowered by style; anyone who stops being one gets MCA's back. */
    private static void applyWaterCost(VillagerEntityMCA villager, SwimProfile profile) {
        if (profile == null) {
            if (ADJUSTED.remove(villager) != null) setWaterCost(villager, MCA_WATER_COST);
            return;
        }
        float cost = switch (profile.style()) {
            case PADDLE -> 2f;
            case DIVE, FISH -> 0f;
        };
        Float current = ADJUSTED.get(villager);
        if (current != null && current == cost) return;
        setWaterCost(villager, cost);
        ADJUSTED.put(villager, cost);
    }

    private static void setWaterCost(VillagerEntityMCA villager, float cost) {
        //? if >=1.21 {
        villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER, cost);
        villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.PathType.WATER_BORDER, cost);
        //?} else {
        /*villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER, cost);
        villager.setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER, cost);
        *///?}
    }

    /** One line for debug reports. */
    public static String describe(VillagerEntityMCA villager) {
        SwimProfile profile = SwimProfile.of(villager);
        if (profile == null) return "not a swimmer";
        Float cost = ADJUSTED.get(villager);
        return profile.describe() + (cost == null ? "" : ", water cost " + cost.intValue())
                + ", " + Locomotion.describe(villager);
    }
}
