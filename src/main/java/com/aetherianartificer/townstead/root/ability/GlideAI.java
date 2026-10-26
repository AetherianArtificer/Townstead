package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.root.locomotion.FlightProfile;
import com.aetherianartificer.townstead.root.locomotion.FlightStyle;
import com.aetherianartificer.townstead.root.locomotion.Locomotion;
import com.aetherianartificer.townstead.root.locomotion.MovementMode;
import com.aetherianartificer.townstead.root.locomotion.Pilot;
import com.aetherianartificer.townstead.root.locomotion.TripCost;
import com.aetherianartificer.townstead.root.locomotion.fly.ElytraPilot;
import com.aetherianartificer.townstead.root.locomotion.fly.HoverPilot;
import com.aetherianartificer.townstead.root.locomotion.fly.PlanThrottle;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoute;
import com.aetherianartificer.townstead.root.locomotion.plan.AirRoutePlanner;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * When winged villagers take to the air. The flying itself belongs to a pilot
 * ({@link ElytraPilot} for glide, flap and soar; {@link HoverPilot} for hover); this
 * class decides when to launch and catches falls. Drift bearers never come through here:
 * {@code DriftPilot} floats them all the time.
 *
 * <ul>
 * <li><b>Fall recovery</b>: any fall past a few blocks deploys the wings (or starts a
 * hover) and plans a landing.</li>
 * <li><b>Trips</b>: flying wins when it is clearly quicker than walking, stamina
 * included ({@link TripCost}), or when walking can't reach at all; a bearer stranded on
 * a perch flies down rather than walking off the edge. Only a planned route launches.</li>
 * <li><b>Joy and panic flights</b>: idle fliers with lift sometimes fly for fun; a
 * threatened one flies away from the threat.</li>
 * </ul>
 *
 * <p>Babies, toddlers and children stay grounded (a caught fall still opens the wings);
 * teens fly shorter trips. Per-tick cost for wingless villagers is one pilot check plus,
 * at 1 Hz, a couple of cheap geometry gates that fail before any gene scan. Planning is throttled per server
 * tick, and a failed plan is not retried for the same trip from the same spot for a while.</p>
 */
public final class GlideAI {

    private GlideAI() {}

    private static final float DEPLOY_FALL_DISTANCE = 2.5f;
    private static final double MIN_DROP = 3.0;
    /** Shorter errands stay walks (chatting with a neighbor, a short wander). */
    private static final double MIN_TRIP = 8.0;
    /** Cut off from the target on foot (a perch, a moat): any hop this long is worth wings. */
    private static final double STRANDED_MIN_TRIP = 3.0;
    private static final double MAX_TRIP = 64.0;
    private static final double TEEN_MAX_TRIP = 32.0;
    private static final double MAX_CLIMB = 12.0;
    /** A route landing this close to where the bearer stands is no trip (the best spot is the perch it is on). */
    private static final double SAME_SPOT_SQR = 4.0;
    private static final int LAUNCH_SCAN_INTERVAL = 20;
    // Idle winged villagers with a lift ability sometimes fly for the joy of it:
    // one roll per scan second, so an average wait of about half a minute.
    private static final int JOY_FLIGHT_CHANCE = 30;
    private static final long FAILED_PLAN_COOLDOWN = 200;
    private static final double FAILED_PLAN_MOVE_SQR = 4.0;

    /** A plan that failed: not retried for this target from about here until the cooldown runs out. */
    private record FailedPlan(BlockPos target, BlockPos from, long until, String reason) {}

    /** Server thread only; weak keys drop entries with the entity. */
    private static final Map<VillagerEntityMCA, FailedPlan> FAILED = new WeakHashMap<>();

    /**
     * Highest Y seen since leaving the ground. Vanilla {@code fallDistance} is
     * useless here: slow falling resets it to zero every travel() tick, so a
     * featherfall bearer's counter never passes any threshold — the wings' own
     * safety net hides every fall from the wings.
     */
    private static final Map<VillagerEntityMCA, Double> FALL_PEAK = new WeakHashMap<>();

    private static final ElytraPilot.Wings WINGS = new ElytraPilot.Wings() {
        @Override
        public boolean deploy(Mob mob) {
            return mob instanceof VillagerEntityMCA villager && deployWings(villager);
        }

        @Override
        public void fold(Mob mob) {
            if (mob instanceof VillagerEntityMCA villager) foldIfDeployed(villager);
        }
    };

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if (Locomotion.pilot(villager) != null) {
            FALL_PEAK.remove(villager);
            return;
        }
        if (villager.isFallFlying()) {
            // Flying with no pilot (a reload mid-flight): pick it up as a caught fall.
            recover(villager);
            return;
        }
        if (villager.onGround() || villager.isInWater() || villager.isPassenger()) {
            FALL_PEAK.remove(villager);
            foldIfDeployed(villager);
            if (villager.onGround() && !villager.isInWater() && !villager.isPassenger()) {
                maybeLaunch(villager);
            }
            return;
        }
        // Threshold on descent from our own tracked peak: featherfall breaks both
        // vanilla measures — slow falling caps speed around 0.1 blocks/tick AND
        // zeroes fallDistance every travel() tick — so any gate on either would mean
        // wings never deploy for exactly the bearers who have them.
        double peak = FALL_PEAK.merge(villager, villager.getY(), Math::max);
        if (peak - villager.getY() > DEPLOY_FALL_DISTANCE && villager.getDeltaMovement().y < -0.05) {
            recover(villager);
        }
    }

    /** Whether a {@code when_flying} lift ability should fire: the pilot is below its route or the route climbs ahead. */
    public static boolean wantsLift(VillagerEntityMCA villager) {
        Pilot pilot = Locomotion.pilot(villager);
        return pilot != null && pilot.wantsLift(villager);
    }

    /** Caught falling: wings out (or hover on) and land somewhere planned, else glide on and keep looking. */
    private static void recover(VillagerEntityMCA villager) {
        FlightProfile profile = flier(villager);
        if (profile == null) return;
        BlockPos target = travelTarget(villager);
        if (target == null) target = landingAhead(villager);
        AirRoute route = null;
        if (target != null && PlanThrottle.tryAcquire(villager.level())) {
            route = AirRoutePlanner.plan(villager, profile, target).route();
        }
        if (profile.style().physics() == FlightStyle.Physics.NO_GRAVITY) {
            Locomotion.start(villager, MovementMode.AIR, new HoverPilot(villager, profile, route, target), target);
        } else {
            Locomotion.start(villager, MovementMode.AIR,
                    new ElytraPilot(villager, profile, route, target, WINGS, false), target);
        }
        FALL_PEAK.remove(villager);
    }

    /**
     * Take to the air when it beats walking:
     *
     * <ul>
     * <li><b>Trip</b>: the travel target is far enough out and a flight there is clearly
     * quicker than the walk (or the walk can't reach). A pure glider needs the target
     * inside its glide cone and waits for the brink.</li>
     * <li><b>Joy flight</b>: no destination at all; idle fliers that can climb occasionally
     * pick a clear spot on the skyline and just go.</li>
     * </ul>
     *
     * <p>Cheap gates run first; gene scans and the planner last.</p>
     */
    private static void maybeLaunch(VillagerEntityMCA villager) {
        if (villager.tickCount % LAUNCH_SCAN_INTERVAL != 0) return;
        if (!oldEnough(villager) || villager.isSleeping()) return;
        // Panic preempts everything: the panic brain sets short flee targets that
        // would fail the trip gates below, yet the air is the one escape a
        // ground threat can't follow.
        if (maybePanicLaunch(villager)) return;
        BlockPos target = travelTarget(villager);
        if (target != null) {
            boolean stranded = !TripCost.walkCanReach(villager);
            double horiz = horizontalTo(villager, target);
            // A doorstep errand shouldn't suppress wanderlust: fall through to the joy roll.
            if (horiz >= (stranded ? STRANDED_MIN_TRIP : MIN_TRIP)) {
                maybeTrip(villager, target, horiz);
                return;
            }
        }
        // Idle: sometimes fly for the joy of it (lift required — a joy glide
        // without the means to climb back is just a stranding).
        if (villager.getRandom().nextInt(JOY_FLIGHT_CHANCE) != 0) return;
        FlightProfile profile = flier(villager);
        if (profile == null || !canClimb(villager, profile)) return;
        BlockPos joyTarget = pickJoyTarget(villager);
        if (joyTarget != null) launch(villager, profile, joyTarget, true, Double.POSITIVE_INFINITY);
    }

    private static void maybeTrip(VillagerEntityMCA villager, BlockPos target, double horiz) {
        double drop = villager.getY() - target.getY();
        if (horiz > maxTrip(villager) || drop < -MAX_CLIMB) return;
        double walkTicks = TripCost.walkTicks(villager, target);
        FlightProfile profile = flier(villager);
        if (profile == null) return;
        boolean climbs = canClimb(villager, profile);
        if (!climbs) {
            boolean glidable = drop >= MIN_DROP && horiz <= drop * profile.style().glideRatio();
            if (!glidable || !clearLine(villager, target)) return;
        }
        if (!TripCost.flightWins(TripCost.estimateFlyTicks(profile.style(), horiz, drop), walkTicks)) return;
        launch(villager, profile, target, climbs, walkTicks);
    }

    /** Teens and adults fly trips; younger villagers stay on the ground. */
    private static boolean oldEnough(VillagerEntityMCA villager) {
        AgeState age = AgeState.byCurrentAge(villager.getAge());
        return age == AgeState.TEEN || age == AgeState.ADULT;
    }

    private static double maxTrip(VillagerEntityMCA villager) {
        return AgeState.byCurrentAge(villager.getAge()) == AgeState.TEEN ? TEEN_MAX_TRIP : MAX_TRIP;
    }

    /** Lift means climbing: a {@code when_flying} ability on wings, or a gravity-free style. */
    private static boolean canClimb(VillagerEntityMCA villager, FlightProfile profile) {
        return profile.style().physics() == FlightStyle.Physics.NO_GRAVITY || FlightProfile.hasLiftAbility(villager);
    }

    private static double horizontalTo(VillagerEntityMCA villager, BlockPos target) {
        double dx = target.getX() + 0.5 - villager.getX();
        double dz = target.getZ() + 0.5 - villager.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Plan the trip; only a planned route launches, and only when it still beats the walk
     * once priced in full. A failure is remembered for this trip from about here.
     */
    private static boolean launch(VillagerEntityMCA villager, FlightProfile profile, BlockPos target, boolean powered,
                                  double walkTicks) {
        long now = villager.level().getGameTime();
        FailedPlan failed = FAILED.get(villager);
        if (failed != null && now < failed.until() && failed.target().equals(target)
                && failed.from().distSqr(villager.blockPosition()) < FAILED_PLAN_MOVE_SQR) {
            return false;
        }
        if (!PlanThrottle.tryAcquire(villager.level())) return false;
        AirRoutePlanner.Result result = AirRoutePlanner.plan(villager, profile, target);
        String failure = result.failure();
        AirRoute route = result.route();
        if (route != null && route.landing().distSqr(villager.blockPosition()) <= SAME_SPOT_SQR) {
            failure = "best landing is where it stands";
            route = null;
        } else if (route != null && !TripCost.flightWins(TripCost.flyTicks(route), walkTicks)) {
            failure = String.format("walking is quicker (%.0f vs %.0f ticks)", walkTicks, TripCost.flyTicks(route));
            route = null;
        }
        if (route == null) {
            FAILED.put(villager, new FailedPlan(target.immutable(), villager.blockPosition(),
                    now + FAILED_PLAN_COOLDOWN, failure));
            return false;
        }
        FAILED.remove(villager);
        if (profile.style().physics() == FlightStyle.Physics.NO_GRAVITY) {
            Locomotion.start(villager, MovementMode.AIR, new HoverPilot(villager, profile, route, target), target);
            return true;
        }
        Vec3 first = route.waypoints().size() > 1 ? route.waypoints().get(1) : route.end();
        double dx = first.x - villager.getX();
        double dz = first.z - villager.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-3) return false;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        villager.setYRot(yaw);
        villager.yBodyRot = yaw;
        villager.setYHeadRot(yaw);
        double up = powered ? 0.9 : 0.5;
        villager.setDeltaMovement(villager.getDeltaMovement().add(dx / horiz * 0.3, up, dz / horiz * 0.3));
        villager.hasImpulse = true;
        Locomotion.start(villager, MovementMode.AIR, new ElytraPilot(villager, profile, route, target, WINGS, true), target);
        return true;
    }

    /**
     * A random clear spot on the local skyline; null if no try fits. Generous try
     * count because cramped terrain (a narrow strip, a courtyard) rejects most
     * directions and the fun dies if the search gives up too easily.
     */
    private static BlockPos pickJoyTarget(VillagerEntityMCA villager) {
        var random = villager.getRandom();
        for (int i = 0; i < 12; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 8.0 + random.nextDouble() * 32.0;
            BlockPos pos = sampleLanding(villager, angle, dist);
            if (pos != null) return pos;
        }
        return null;
    }

    /**
     * Airborne escape: something is hunting this villager, and the air is where a
     * ground threat can't follow. Launch toward a clear landing in the half-plane
     * away from the threat. Repeats naturally — if still hunted after touchdown,
     * the next scan launches again.
     */
    private static boolean maybePanicLaunch(VillagerEntityMCA villager) {
        LivingEntity threat = threat(villager);
        if (threat == null) return false;
        FlightProfile profile = flier(villager);
        if (profile == null || !canClimb(villager, profile)) return false;
        double away = Math.atan2(villager.getZ() - threat.getZ(), villager.getX() - threat.getX());
        var random = villager.getRandom();
        for (int i = 0; i < 12; i++) {
            double angle = away + (random.nextDouble() - 0.5) * (Math.PI * 2.0 / 3.0);
            double dist = 16.0 + random.nextDouble() * 24.0;
            BlockPos pos = sampleLanding(villager, angle, dist);
            if (pos != null) return launch(villager, profile, pos, true, Double.POSITIVE_INFINITY);
        }
        return false;
    }

    /** Something alive, nearby, and recently hostile to this villager; null when safe. */
    private static LivingEntity threat(VillagerEntityMCA villager) {
        LivingEntity hurtBy = villager.getLastHurtByMob();
        if (hurtBy != null && hurtBy.isAlive() && villager.distanceToSqr(hurtBy) < 24.0 * 24.0) {
            return hurtBy;
        }
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE)) {
            return villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null);
        }
        return null;
    }

    /**
     * One destination sample for a joy or panic flight: the terrain surface at the
     * offset, or null if it fails a cheap filter. The planner picks the actual
     * landing spot near it.
     */
    private static BlockPos sampleLanding(VillagerEntityMCA villager, double angle, double dist) {
        int x = Mth.floor(villager.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(villager.getZ() + Math.sin(angle) * dist);
        if (!villager.level().hasChunk(x >> 4, z >> 4)) return null;
        int y = villager.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // The heightmap counts fluid surfaces; a flight ending in a splash isn't a landing.
        if (!villager.level().getFluidState(pos.below()).isEmpty()) return null;
        double drop = villager.getY() - pos.getY();
        // 24-block drop cap: flights must not strand a villager below its home range.
        if (drop < -MAX_CLIMB || drop > 24.0) return null;
        if (!clearLine(villager, pos)) return null;
        return pos;
    }

    /** Somewhere ahead and below to come down when a fall has no destination. */
    private static BlockPos landingAhead(VillagerEntityMCA villager) {
        Vec3 look = villager.getLookAngle();
        double ahead = Math.atan2(look.z, look.x);
        int x = Mth.floor(villager.getX() + Math.cos(ahead) * 12.0);
        int z = Mth.floor(villager.getZ() + Math.sin(ahead) * 12.0);
        if (!villager.level().hasChunk(x >> 4, z >> 4)) return null;
        return new BlockPos(x, villager.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /**
     * Gate-by-gate report for {@code /townstead debug glide}: mirrors {@link #maybeLaunch}
     * so a "they just stand there" report names the blocking gate instead of guessing.
     * Kept adjacent to the logic it mirrors — update both together.
     */
    public static String diagnose(VillagerEntityMCA villager) {
        StringBuilder out = new StringBuilder(villager.getName().getString());
        FlightProfile profile = FlightProfile.of(villager);
        if (profile == null) return out.append(": no flight gene, never flies").toString();
        boolean lift = FlightProfile.hasLiftAbility(villager);
        out.append(": flight gene ").append(profile.power().id().getPath()).append(", ").append(profile.describe());
        if (profile.style() != FlightStyle.DRIFT) {
            out.append(lift ? " + lift ability" : canClimb(villager, profile) ? "" : ", NO lift ability (edge glides only)");
        }
        Pilot pilot = Locomotion.pilot(villager);
        if (pilot != null) return out.append("\n  ").append(Locomotion.describe(villager)).toString();
        if (profile.style() == FlightStyle.DRIFT) return out.append("\n  drift starts within a second").toString();
        boolean climbs = canClimb(villager, profile);
        if (!oldEnough(villager)) return out.append(" — too young for trips").toString();
        if (villager.isSleeping()) return out.append(" — sleeping").toString();
        if (villager.isInWater()) return out.append(" — in water").toString();
        FailedPlan failed = FAILED.get(villager);
        if (failed != null && villager.level().getGameTime() < failed.until()) {
            out.append("\n  last plan to ").append(failed.target().toShortString())
                    .append(" failed: ").append(failed.reason());
        }
        LivingEntity threat = threat(villager);
        if (threat != null) {
            out.append(" — THREATENED by ").append(threat.getName().getString())
                    .append(climbs ? " (escape flight when a clear spot exists)" : " (flees on foot, no lift)");
        }
        BlockPos target = travelTarget(villager);
        boolean stranded = !TripCost.walkCanReach(villager);
        if (target != null && horizontalTo(villager, target) >= (stranded ? STRANDED_MIN_TRIP : MIN_TRIP)) {
            double drop = villager.getY() - target.getY();
            double horiz = horizontalTo(villager, target);
            out.append(String.format("\n  travel target %s: %.0f out, %.0f %s, ",
                    target.toShortString(), horiz, Math.abs(drop), drop >= 0 ? "down" : "UP"));
            double walkTicks = TripCost.walkTicks(villager, target);
            double flyTicks = TripCost.estimateFlyTicks(profile.style(), horiz, drop);
            out.append(Double.isInfinite(walkTicks) ? "NO ground path, " : String.format("walk ~%.0ft, ", walkTicks))
                    .append(String.format("fly ~%.0ft: ", flyTicks));
            if (horiz > maxTrip(villager)) {
                out.append("beyond flight range (" + (int) maxTrip(villager) + ")");
            } else if (drop < -MAX_CLIMB) {
                out.append("too far up (max climb " + (int) MAX_CLIMB + ")");
            } else if (!climbs && !(drop >= MIN_DROP && horiz <= drop * profile.style().glideRatio())) {
                out.append("outside glide cone, no lift: walks");
            } else if (!climbs && !clearLine(villager, target)) {
                out.append("in glide cone, line blocked (not at the brink)");
            } else if (!TripCost.flightWins(flyTicks, walkTicks)) {
                out.append("walking is quicker");
            } else {
                out.append("will plan a flight");
            }
            return out.toString();
        }
        out.append("\n  idle");
        if (target != null) out.append(" (doorstep errand)");
        if (!climbs) return out.append(" — no lift, no joy flights").toString();
        int ok = 0;
        int waterRejects = 0;
        int heightRejects = 0;
        int lineRejects = 0;
        var random = villager.getRandom();
        for (int i = 0; i < 24; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 8.0 + random.nextDouble() * 32.0;
            int x = Mth.floor(villager.getX() + Math.cos(angle) * dist);
            int z = Mth.floor(villager.getZ() + Math.sin(angle) * dist);
            if (!villager.level().hasChunk(x >> 4, z >> 4)) continue;
            int y = villager.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            double drop = villager.getY() - pos.getY();
            if (!villager.level().getFluidState(pos.below()).isEmpty()) waterRejects++;
            else if (drop < -MAX_CLIMB || drop > 24.0) heightRejects++;
            else if (!clearLine(villager, pos)) lineRejects++;
            else ok++;
        }
        out.append(String.format(" — joy flight ~1/%ds; targets: %d/24 samples valid (%d water, %d height-window, %d line-blocked)",
                JOY_FLIGHT_CHANCE, ok, waterRejects, heightRejects, lineRejects));
        return out.toString();
    }

    /** Wings out: switch a toggle gene on (as a player would), set the glide flag. False for a wingless bearer. */
    private static boolean deployWings(VillagerEntityMCA villager) {
        Power power = glidePower(villager);
        if (power == null) return false;
        ResourceLocation gene = power.id();
        if (isToggle(power) && !AbilityToggles.isOn(villager, gene)) {
            AbilityToggles.set(villager, gene, true);
            AbilityToggles.syncEntity(villager);
        }
        ((FallFlightBridge) villager).townstead$setFallFlying(true);
        return true;
    }

    /** Vanilla clears the fall-flight flag on touchdown; this folds the wings after it. */
    private static void foldIfDeployed(VillagerEntityMCA villager) {
        if (!AbilityToggles.hasAny(villager)) return;
        Power power = glidePower(villager);
        if (power == null || !isToggle(power)) return;     // passive wings have nothing to fold away
        ResourceLocation gene = power.id();
        if (!AbilityToggles.isOn(villager, gene)) return;
        AbilityToggles.set(villager, gene, false);
        AbilityToggles.syncEntity(villager);
    }

    /** The bearer's flight profile if it flies trips (drift floats instead), else null. */
    private static FlightProfile flier(VillagerEntityMCA villager) {
        FlightProfile profile = FlightProfile.of(villager);
        return profile == null || profile.style() == FlightStyle.DRIFT ? null : profile;
    }

    private static BlockPos travelTarget(VillagerEntityMCA villager) {
        return Locomotion.destination(villager);
    }

    /** One raycast along the glide chord; blocked terrain also means "not at the brink yet". */
    private static boolean clearLine(VillagerEntityMCA villager, BlockPos target) {
        Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 1.5, target.getZ() + 0.5);
        return villager.level().clip(new ClipContext(villager.getEyePosition(), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager)).getType() == HitResult.Type.MISS;
    }

    /**
     * The bearer's elytra-flight power: a toggle-mode one (deployed by switching it on, as a player
     * would), or a passive one (wings always ready, deployed just by taking off).
     */
    private static Power glidePower(VillagerEntityMCA villager) {
        for (Power gene : com.aetherianartificer.townstead.pheno.power.Powers.active(villager)) {
            if (gene.component() instanceof AbilityGeneType.Instance ability
                    && ability.ability() == Ability.ELYTRA_FLIGHT
                    && (ability.mode() == AbilityGeneType.Mode.TOGGLE || ability.mode() == AbilityGeneType.Mode.PASSIVE)) {
                return gene;
            }
        }
        return null;
    }

    private static boolean isToggle(Power power) {
        return power.component() instanceof AbilityGeneType.Instance ability
                && ability.mode() == AbilityGeneType.Mode.TOGGLE;
    }
}
