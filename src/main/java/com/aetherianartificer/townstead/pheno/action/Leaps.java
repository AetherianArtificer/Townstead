package com.aetherianartificer.townstead.pheno.action;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Transient leap state; only the height actually gained is deducted from the next landing. */
public final class Leaps {
    private Leaps() {}
    private static final Map<LivingEntity, Flight> FLIGHTS = new WeakHashMap<>();
    public static void clear() { FLIGHTS.clear(); }
    private static final class Flight {
        final ServerLevel level;
        final double startY;
        final long expires;
        double peakY;
        Vec3 previous;
        boolean airborne;
        Flight(LivingEntity e, ServerLevel level) {
            this.level = level; startY = peakY = e.getY(); previous = e.position();
            expires = level.getGameTime() + 200;
        }
    }

    public static boolean start(LivingEntity e, double up, double forward) {
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive() || !e.onGround()
                || e.isPassenger() || e.isInWaterOrBubble() || e.isInLava()
                || e.isFallFlying() || e.isSleeping() || FLIGHTS.containsKey(e)) return false;
        Vec3 direction = e.getLookAngle().multiply(1, 0, 1).normalize();
        if (!(e instanceof Player)) {
            // A remembered attacker can remain set long after combat. Only escape a recent,
            // nearby threat, and launch away from it rather than toward the current look target.
            LivingEntity threat = e.getLastHurtByMob();
            if (threat == null || !threat.isAlive() || e.tickCount - e.getLastHurtByMobTimestamp() > 100
                    || e.distanceToSqr(threat) > 64) return false;
            direction = e.position().subtract(threat.position()).multiply(1, 0, 1).normalize();
        }
        Vec3 velocity = direction.scale(forward).add(0, up, 0);
        // Validate a complete flight for mobs, including a supported, dry landing. Players
        // control their own destination, but still cannot launch into a low roof.
        if (!clearLaunch(e, up) || (!(e instanceof Player) && !safeArc(e, velocity))) return false;
        FLIGHTS.put(e, new Flight(e, level));
        if (e instanceof Mob mob) mob.getNavigation().stop();
        e.setDeltaMovement(velocity);
        e.hasImpulse = true;
        if (e instanceof ServerPlayer player)
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        return true;
    }

    private static boolean clearLaunch(LivingEntity e, double up) {
        return e.level().noCollision(e, e.getBoundingBox().expandTowards(0, up, 0).deflate(.001));
    }

    public static boolean safeArc(LivingEntity e, Vec3 velocity) {
        AABB box = e.getBoundingBox().deflate(.001);
        Vec3 offset = Vec3.ZERO;
        //? if neoforge {
        double gravity = e.getGravity();
        //?} else {
        /*double gravity = .08;
        *///?}
        if (!Double.isFinite(gravity) || gravity <= 0) return false;
        for (int tick = 0; tick < 80; tick++) {
            AABB next = box.move(offset).expandTowards(velocity);
            BlockPos at = BlockPos.containing(next.getCenter());
            if (!e.level().hasChunkAt(at) || !e.level().getWorldBorder().isWithinBounds(next)
                    || e.level().containsAnyLiquid(next)
                    || e.level().getBlockStates(next).anyMatch(Leaps::hazardous)) return false;
            if (!e.level().noCollision(e, next)) {
                // Only accept a landing on the original floor (or higher), with room above
                // it. Side collisions, roofs, cliffs and hazardous surfaces are rejected.
                AABB horizontal = box.move(offset).expandTowards(velocity.x, 0, velocity.z);
                if (velocity.y >= 0 || !e.level().noCollision(e, horizontal)) return false;
                Vec3 feet = e.position().add(offset).add(velocity.x, 0, velocity.z);
                BlockPos floor = BlockPos.containing(feet.x, feet.y + velocity.y - .01, feet.z);
                var state = e.level().getBlockState(floor);
                return feet.y + velocity.y >= e.getY() - .5
                        && state.isFaceSturdy(e.level(), floor, net.minecraft.core.Direction.UP)
                        && !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        && !state.is(net.minecraft.world.level.block.Blocks.CAMPFIRE)
                        && !state.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)
                        && !state.is(net.minecraft.world.level.block.Blocks.CACTUS);
            }
            offset = offset.add(velocity);
            velocity = velocity.multiply(.91, 1, .91).add(0, -gravity, 0).multiply(1, .98, 1);
        }
        return false;
    }

    private static boolean hazardous(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.FIRE)
                || state.is(net.minecraft.world.level.block.Blocks.WITHER_ROSE)
                || state.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH)
                || state.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
    }

    public static void tick(LivingEntity e) {
        Flight f = FLIGHTS.get(e);
        if (f == null) return;
        if (!e.isAlive() || e.level() != f.level || f.level.getGameTime() > f.expires
                || e.position().distanceToSqr(f.previous) > 16 || e.isPassenger()
                || e.isInWaterOrBubble() || e.isFallFlying() || (f.airborne && e.onGround())) {
            FLIGHTS.remove(e);
            return;
        }
        f.airborne |= !e.onGround();
        f.peakY = Math.max(f.peakY, e.getY());
        f.previous = e.position();
    }

    public static float landingDistance(LivingEntity e, float distance) {
        Flight f = FLIGHTS.remove(e);
        if (f == null || e.level() != f.level || f.level.getGameTime() > f.expires
                || e.position().distanceToSqr(f.previous) > 16) return distance;
        return Math.max(0, distance - (float)Math.max(0, f.peakY - f.startY));
    }
}
