package com.aetherianartificer.townstead.root.ability;

import com.aetherianartificer.townstead.root.gene.types.AbilityGeneType;
import com.aetherianartificer.townstead.root.gene.types.ActiveAbilityGeneType;
import com.aetherianartificer.townstead.pheno.power.Power;
import com.aetherianartificer.townstead.pheno.power.Powers;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Flight AI for winged villagers (bearers of a toggle-mode {@code elytra_flight}
 * gene). Two ways into the air:
 *
 * <ul>
 * <li><b>Fall recovery</b>: any fall past a few blocks deploys the wings.</li>
 * <li><b>Island hop</b>: when the travel target sits inside the glide cone (far
 * enough out, low enough down, straight line clear), jump off and glide there
 * deliberately. No aerial A*: a glide cannot climb, so the whole air path is one
 * chord — feasibility is a cone test plus a single raycast, and the launch only
 * fires once terrain stops blocking that ray, i.e. at the brink. Landing short is
 * self-healing: fold, walk, scan again.</li>
 * </ul>
 *
 * <p>Mid-air they steer toward the target, dive to build airspeed then flatten to
 * cruise (with a lift assist trimming the sink — villagers are born fliers), and
 * flare onto the landing spot when close. Wings fold on touchdown.</p>
 *
 * <p>Per-tick cost for wingless villagers is one {@code isFallFlying} check plus,
 * at 1 Hz, a couple of cheap geometry gates that fail before any gene scan.</p>
 */
public final class GlideAI {

    private GlideAI() {}

    private static final float DEPLOY_FALL_DISTANCE = 2.5f;
    private static final float GLIDE_PITCH = 18f;
    private static final float CRUISE_PITCH = 6f;
    private static final float FLARE_DISTANCE = 12f;
    // Conservative vs the assisted glide ratio so launches never overpromise.
    private static final double GLIDE_RATIO = 3.0;
    private static final double MIN_DROP = 3.0;
    private static final double MIN_HORIZONTAL = 8.0;
    // Powered launches (bearer has a when_flying lift ability): worth the stamina
    // only for real trips, and the pump budget caps range and climb.
    private static final double POWERED_MIN_HORIZONTAL = 20.0;
    private static final double POWERED_MAX_HORIZONTAL = 48.0;
    private static final double POWERED_MAX_CLIMB = 6.0;
    private static final int LAUNCH_SCAN_INTERVAL = 20;
    // Idle winged villagers with a lift ability sometimes fly for the joy of it:
    // one roll per scan second, so an average wait of about half a minute.
    private static final int JOY_FLIGHT_CHANCE = 30;

    /** A deliberate launch: hop this tick, deploy on the first airborne tick. */
    private static final class Launch {
        final BlockPos target;
        final long deployBy;
        boolean deployed;

        Launch(BlockPos target, long deployBy) {
            this.target = target;
            this.deployBy = deployBy;
        }
    }

    /** Server thread only; weak keys drop entries with the entity. */
    private static final Map<VillagerEntityMCA, Launch> LAUNCHES = new WeakHashMap<>();

    /**
     * Highest Y seen since leaving the ground. Vanilla {@code fallDistance} is
     * useless here: slow falling resets it to zero every travel() tick, so a
     * featherfall bearer's counter never passes any threshold — the wings' own
     * safety net hides every fall from the wings.
     */
    private static final Map<VillagerEntityMCA, Double> FALL_PEAK = new WeakHashMap<>();

    public static void tick(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) return;
        if (villager.isFallFlying()) {
            steer(villager);
            return;
        }
        Launch launch = LAUNCHES.get(villager);
        if (launch != null && !launch.deployed) {
            if (villager.level().getGameTime() > launch.deployBy) {
                LAUNCHES.remove(villager);
            } else if (!villager.onGround()) {
                deploy(villager);
                launch.deployed = true;
                return;
            }
        }
        if (villager.onGround() || villager.isInWater() || villager.isPassenger()) {
            FALL_PEAK.remove(villager);
            foldIfDeployed(villager);
            if (launch != null && launch.deployed) LAUNCHES.remove(villager);
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
            deploy(villager);
        }
    }

    /**
     * Whether extra lift would help right now: gliding toward a target the remaining
     * glide cone can no longer reach (falling short, or the target sits above). False
     * during the landing flare and on target-less recovery glides, so a lift ability
     * (ai_trigger {@code when_flying}) never shoves a villager off its landing spot.
     */
    public static boolean wantsLift(VillagerEntityMCA villager) {
        if (!villager.isFallFlying()) return false;
        BlockPos target = travelTarget(villager);
        if (target == null) {
            Launch launch = LAUNCHES.get(villager);
            if (launch == null) return false;
            target = launch.target;
        }
        double dx = target.getX() + 0.5 - villager.getX();
        double dz = target.getZ() + 0.5 - villager.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < FLARE_DISTANCE) return false;
        return villager.getY() - target.getY() < horiz / GLIDE_RATIO;
    }

    /**
     * Take to the air when it beats walking. Three kinds of launch:
     *
     * <ul>
     * <li><b>Glide</b>: the travel target sits inside the glide cone — hop off the
     * edge and coast there on wings alone.</li>
     * <li><b>Powered</b>: the target is far but level (or a little up) and the bearer
     * carries a {@code when_flying} lift ability — take off from flat ground and let
     * the pumps carry the climb.</li>
     * <li><b>Joy flight</b>: no destination at all; idle winged fliers with a lift
     * ability occasionally pick a clear spot on the skyline and just go.</li>
     * </ul>
     *
     * <p>Cheap gates run first; gene scans last. The chord raycast doubles as the
     * "wait for the brink / for open air" test.</p>
     */
    private static void maybeLaunch(VillagerEntityMCA villager) {
        if (villager.tickCount % LAUNCH_SCAN_INTERVAL != 0) return;
        if (villager.isBaby() || villager.isSleeping() || LAUNCHES.containsKey(villager)) return;
        // Panic preempts everything: the panic brain sets short flee targets that
        // would fail the launch envelope below, yet the air is the one escape a
        // ground threat can't follow.
        if (maybePanicLaunch(villager)) return;
        BlockPos target = travelTarget(villager);
        // A doorstep errand (chatting with a neighbor, a short wander) shouldn't
        // suppress wanderlust — fall through to the joy roll instead of bailing.
        if (target != null && horizontalTo(villager, target) >= MIN_HORIZONTAL) {
            double drop = villager.getY() - target.getY();
            double dx = target.getX() + 0.5 - villager.getX();
            double dz = target.getZ() + 0.5 - villager.getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);
            boolean glidable = drop >= MIN_DROP && horiz <= drop * GLIDE_RATIO;
            // The powered minimum exists so short walkable errands stay walks; when
            // the ground path can't reach the target at all (a gap, a moat), a short
            // flight is the whole point of having wings — any launchable distance goes.
            double poweredMin = groundCanReach(villager) ? POWERED_MIN_HORIZONTAL : MIN_HORIZONTAL;
            if (!glidable && (horiz < poweredMin || horiz > POWERED_MAX_HORIZONTAL
                    || drop < -POWERED_MAX_CLIMB)) {
                return;
            }
            if (!clearLine(villager, target)) return;
            if (glideGene(villager) == null) return;
            if (!glidable && !hasLiftAbility(villager)) return;
            launch(villager, target, !glidable);
            return;
        }
        // Idle: sometimes fly for the joy of it (lift ability required — a joy glide
        // without the means to climb back is just a stranding).
        if (villager.getRandom().nextInt(JOY_FLIGHT_CHANCE) != 0) return;
        if (glideGene(villager) == null || !hasLiftAbility(villager)) return;
        BlockPos joyTarget = pickJoyTarget(villager);
        if (joyTarget != null) launch(villager, joyTarget, true);
    }

    private static double horizontalTo(VillagerEntityMCA villager, BlockPos target) {
        double dx = target.getX() + 0.5 - villager.getX();
        double dz = target.getZ() + 0.5 - villager.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Whether ground navigation currently has a complete path to its target. */
    private static boolean groundCanReach(VillagerEntityMCA villager) {
        Path path = villager.getNavigation().getPath();
        return path != null && path.canReach();
    }

    /** Face the destination, hop (higher when the climb is on wingbeats), record the launch. */
    private static void launch(VillagerEntityMCA villager, BlockPos target, boolean powered) {
        double dx = target.getX() + 0.5 - villager.getX();
        double dz = target.getZ() + 0.5 - villager.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-3) return;
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        villager.setYRot(yaw);
        villager.yBodyRot = yaw;
        villager.setYHeadRot(yaw);
        double up = powered ? 0.9 : 0.5;
        villager.setDeltaMovement(villager.getDeltaMovement().add(dx / horiz * 0.3, up, dz / horiz * 0.3));
        villager.hasImpulse = true;
        villager.getNavigation().stop();
        LAUNCHES.put(villager, new Launch(target, villager.level().getGameTime() + 10));
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
        if (glideGene(villager) == null || !hasLiftAbility(villager)) return false;
        double away = Math.atan2(villager.getZ() - threat.getZ(), villager.getX() - threat.getX());
        var random = villager.getRandom();
        for (int i = 0; i < 12; i++) {
            double angle = away + (random.nextDouble() - 0.5) * (Math.PI * 2.0 / 3.0);
            double dist = 16.0 + random.nextDouble() * 24.0;
            BlockPos pos = sampleLanding(villager, angle, dist);
            if (pos != null) {
                launch(villager, pos, true);
                return true;
            }
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

    /** One landing-spot sample: heightmap surface at the offset, or null if it fails a filter. */
    private static BlockPos sampleLanding(VillagerEntityMCA villager, double angle, double dist) {
        // 24-block drop cap: launches must not strand a villager below its home range.
        return sampleLanding(villager, angle, dist, 24.0);
    }

    private static BlockPos sampleLanding(VillagerEntityMCA villager, double angle, double dist, double maxDrop) {
        int x = Mth.floor(villager.getX() + Math.cos(angle) * dist);
        int z = Mth.floor(villager.getZ() + Math.sin(angle) * dist);
        int y = villager.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        // The heightmap counts fluid surfaces; a flight ending in a splash isn't a landing.
        if (!villager.level().getFluidState(pos.below()).isEmpty()) return null;
        double drop = villager.getY() - pos.getY();
        if (drop < -POWERED_MAX_CLIMB || drop > maxDrop) return null;
        if (!clearLine(villager, pos)) return null;
        return pos;
    }

    /**
     * Gate-by-gate report for {@code /townstead debug glide}: mirrors {@link #maybeLaunch}
     * so a "they just stand there" report names the blocking gate instead of guessing.
     * Kept adjacent to the logic it mirrors — update both together.
     */
    public static String diagnose(VillagerEntityMCA villager) {
        StringBuilder out = new StringBuilder(villager.getName().getString());
        ResourceLocation gene = glideGene(villager);
        if (gene == null) return out.append(": no toggle-mode elytra_flight gene — never flies").toString();
        boolean lift = hasLiftAbility(villager);
        out.append(": flight gene ").append(gene.getPath())
                .append(lift ? " + lift ability" : ", NO lift ability (edge glides only)");
        if (villager.isFallFlying()) return out.append(" — FLYING now").toString();
        if (villager.isBaby()) return out.append(" — baby, launches off").toString();
        if (villager.isSleeping()) return out.append(" — sleeping").toString();
        if (villager.isInWater()) return out.append(" — in water").toString();
        if (LAUNCHES.containsKey(villager)) out.append(" — launch pending");
        LivingEntity threat = threat(villager);
        if (threat != null) {
            out.append(" — THREATENED by ").append(threat.getName().getString())
                    .append(lift ? " (escape launch when a clear spot exists)" : " (flees on foot, no lift)");
        }
        BlockPos target = travelTarget(villager);
        if (target != null && horizontalTo(villager, target) >= MIN_HORIZONTAL) {
            double drop = villager.getY() - target.getY();
            double horiz = horizontalTo(villager, target);
            out.append(String.format("\n  travel target %s: %.0f out, %.0f %s — ",
                    target.toShortString(), horiz, Math.abs(drop), drop >= 0 ? "down" : "UP"));
            boolean glidable = drop >= MIN_DROP && horiz <= drop * GLIDE_RATIO;
            boolean canWalk = groundCanReach(villager);
            out.append(canWalk ? "(walkable) " : "(NO ground path) ");
            if (glidable) {
                out.append(clearLine(villager, target) ? "glide launch READY" : "in glide cone, line blocked (not at the brink)");
            } else if (!lift) {
                out.append("outside glide cone, no lift — walks");
            } else if (horiz < (canWalk ? POWERED_MIN_HORIZONTAL : MIN_HORIZONTAL)) {
                out.append("too close for a powered takeoff (needs " + (int) POWERED_MIN_HORIZONTAL + "+ when walkable)");
            } else if (horiz > POWERED_MAX_HORIZONTAL) {
                out.append("beyond powered range (" + (int) POWERED_MAX_HORIZONTAL + ")");
            } else if (drop < -POWERED_MAX_CLIMB) {
                out.append("too far up (max climb " + (int) POWERED_MAX_CLIMB + ")");
            } else {
                out.append(clearLine(villager, target) ? "powered takeoff READY" : "line blocked");
            }
            return out.toString();
        }
        out.append("\n  idle");
        if (target != null) out.append(" (doorstep errand)");
        if (!lift) return out.append(" — no lift ability, no joy flights").toString();
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
            int y = villager.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            double drop = villager.getY() - pos.getY();
            if (!villager.level().getFluidState(pos.below()).isEmpty()) waterRejects++;
            else if (drop < -POWERED_MAX_CLIMB || drop > 24.0) heightRejects++;
            else if (!clearLine(villager, pos)) lineRejects++;
            else ok++;
        }
        out.append(String.format(" — joy flight ~1/%ds; targets: %d/24 samples valid (%d water, %d height-window, %d line-blocked)",
                JOY_FLIGHT_CHANCE, ok, waterRejects, heightRejects, lineRejects));
        return out.toString();
    }

    /** Whether the bearer has an active ability the AI may pump mid-flight for lift. */
    private static boolean hasLiftAbility(VillagerEntityMCA villager) {
        for (Power gene : Powers.active(villager)) {
            if (gene.component() instanceof ActiveAbilityGeneType.Instance active
                    && active.aiTrigger() == ActiveAbilityGeneType.AiTrigger.WHEN_FLYING) {
                return true;
            }
        }
        return false;
    }

    private static void deploy(VillagerEntityMCA villager) {
        ResourceLocation gene = glideGene(villager);
        if (gene == null) return;
        if (!AbilityToggles.isOn(villager, gene)) {
            AbilityToggles.set(villager, gene, true);
            AbilityToggles.syncEntity(villager);
        }
        ((FallFlightBridge) villager).townstead$setFallFlying(true);
        // A real launch toward the destination: featherfall leaves almost no airspeed,
        // and a glide without airspeed is a decorated descent. Aim the push at the
        // travel target when there is one so the takeoff already points somewhere.
        BlockPos target = travelTarget(villager);
        if (target == null) {
            Launch launch = LAUNCHES.get(villager);
            if (launch != null) target = launch.target;
        }
        if (target == null) {
            // A glider with no destination cruises on the lift assist until the world
            // runs out (a fall-deploy has no launch record). Pick an emergency landing
            // roughly ahead — any drop is fine, flying somewhere beats flying forever.
            var look = villager.getLookAngle();
            double ahead = Math.atan2(look.z, look.x);
            var random = villager.getRandom();
            for (int i = 0; i < 8 && target == null; i++) {
                double angle = ahead + (random.nextDouble() - 0.5) * (Math.PI * 2.0 / 3.0);
                target = sampleLanding(villager, angle, 10.0 + random.nextDouble() * 24.0, 256.0);
            }
            if (target != null) {
                Launch launch = new Launch(target, villager.level().getGameTime());
                launch.deployed = true;
                LAUNCHES.put(villager, launch);
            }
        }
        double dx;
        double dz;
        if (target != null) {
            dx = target.getX() + 0.5 - villager.getX();
            dz = target.getZ() + 0.5 - villager.getZ();
        } else {
            var look = villager.getLookAngle();
            dx = look.x;
            dz = look.z;
        }
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length > 1.0e-3) {
            villager.setDeltaMovement(villager.getDeltaMovement()
                    .add(dx / length * 0.6, 0.1, dz / length * 0.6));
            villager.hasImpulse = true;
        }
    }

    /** Vanilla clears the fall-flight flag on touchdown; this folds the wings after it. */
    private static void foldIfDeployed(VillagerEntityMCA villager) {
        if (!AbilityToggles.hasAny(villager)) return;
        ResourceLocation gene = glideGene(villager);
        if (gene == null || !AbilityToggles.isOn(villager, gene)) return;
        AbilityToggles.set(villager, gene, false);
        AbilityToggles.syncEntity(villager);
    }

    /**
     * Elytra physics steers by the look angle: aim at the travel target, dive while
     * slow to build airspeed, then flatten out and cruise with a lift assist trimming
     * the sink. Near the landing spot the assist yields to a flare — the nose points
     * at the spot itself so they touch down on it instead of sailing past.
     */
    private static void steer(VillagerEntityMCA villager) {
        BlockPos target = travelTarget(villager);
        if (target == null) {
            Launch launch = LAUNCHES.get(villager);
            if (launch != null) target = launch.target;
        }
        float yaw = villager.getYRot();
        double horiz = Double.MAX_VALUE;
        if (target != null) {
            double dx = target.getX() + 0.5 - villager.getX();
            double dz = target.getZ() + 0.5 - villager.getZ();
            horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz > 1.0) {
                yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
            }
        }
        villager.setYRot(yaw);
        villager.yBodyRot = yaw;
        villager.setYHeadRot(yaw);
        var motion = villager.getDeltaMovement();
        if (target == null) {
            // No destination: hold a descent and no lift assist, so the flight ends
            // nearby instead of cruising off the map.
            villager.setXRot(GLIDE_PITCH);
            return;
        }
        if (horiz < FLARE_DISTANCE) {
            double drop = villager.getY() - (target.getY() + 1.0);
            villager.setXRot(Mth.clamp((float) Math.toDegrees(Math.atan2(drop, horiz)), -10f, 40f));
            // Air brake: arriving fast means overshooting, and every overshoot flips
            // the steering around for another pass — the back-and-forth thrash.
            double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
            if (speed > 0.35) {
                villager.setDeltaMovement(motion.x * 0.85, motion.y, motion.z * 0.85);
            }
            // Over the spot with little height left: commit — cut flight and let
            // featherfall set them down. Elytra physics may leave one block of fall
            // distance banked, and the staggered passive ticker may not restore Slow
            // Falling before touchdown; clear that bank here so the short handoff
            // cannot become one point of fall damage. Peak reset keeps the short drop
            // from re-triggering a deploy mid-touchdown.
            if (horiz < 2.5 && drop < DEPLOY_FALL_DISTANCE) {
                ((FallFlightBridge) villager).townstead$setFallFlying(false);
                villager.resetFallDistance();
                FALL_PEAK.put(villager, villager.getY());
            }
            return;
        }
        double airspeed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        villager.setXRot(airspeed < 0.5 ? GLIDE_PITCH : CRUISE_PITCH);
        if (motion.y < -0.06) {
            villager.setDeltaMovement(motion.x, motion.y * 0.7, motion.z);
        }
    }

    /** Where the villager is trying to go: live path target, else the brain's walk target. */
    private static BlockPos travelTarget(VillagerEntityMCA villager) {
        Path path = villager.getNavigation().getPath();
        if (path != null) return path.getTarget();
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) return null;
        WalkTarget walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        return walk == null ? null : walk.getTarget().currentBlockPosition();
    }

    /** One raycast along the glide chord; blocked terrain also means "not at the brink yet". */
    private static boolean clearLine(VillagerEntityMCA villager, BlockPos target) {
        Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 1.5, target.getZ() + 0.5);
        return villager.level().clip(new ClipContext(villager.getEyePosition(), to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, villager)).getType() == HitResult.Type.MISS;
    }

    /** The bearer's toggle-mode elytra-flight gene, or null. Only called at deploy/fold/launch moments. */
    private static ResourceLocation glideGene(VillagerEntityMCA villager) {
        for (Power gene : Powers.active(villager)) {
            if (gene.component() instanceof AbilityGeneType.Instance ability
                    && ability.ability() == Ability.ELYTRA_FLIGHT
                    && ability.mode() == AbilityGeneType.Mode.TOGGLE) {
                return gene.id();
            }
        }
        return null;
    }
}
