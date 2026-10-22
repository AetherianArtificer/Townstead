package com.aetherianartificer.townstead.pheno.combat;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Dashes started by {@code pheno:dash}: a burst of movement over a few ticks that stops at walls
 * and can strike the first creatures in its path. The dasher is driven by velocity each tick, so
 * vanilla collision keeps them out of blocks and players see smooth motion; a dash that runs into
 * a wall ends early instead of grinding against it.
 *
 * <p>Transient: one dash per entity at a time; starting another replaces it.</p>
 */
public final class Dashes {

    public record Spec(double distance, int ticks, boolean horizontal, boolean stopOnHit, int maxHits,
                       double hitRadius, @Nullable Action onHit, @Nullable Action onEnd) {}

    private static final class Dash {
        final Spec spec;
        final ServerLevel level;
        final Vec3 direction;
        final double speed;
        final Set<UUID> hit = new HashSet<>();
        int ticksLeft;
        double travelled;
        Vec3 previous;

        Dash(LivingEntity e, ServerLevel level, Spec spec, Vec3 direction) {
            this.spec = spec;
            this.level = level;
            this.direction = direction;
            this.speed = spec.distance() / spec.ticks();
            this.ticksLeft = spec.ticks();
            this.previous = e.position();
        }
    }

    private static final Map<LivingEntity, Dash> DASHES = new WeakHashMap<>();

    private Dashes() {}

    /** Begin a dash along the entity's look; false when it cannot move at all. */
    public static boolean start(LivingEntity e, Spec spec) {
        if (!(e.level() instanceof ServerLevel level) || !e.isAlive() || e.isPassenger() || e.isSleeping()) {
            return false;
        }
        Vec3 look = e.getLookAngle();
        if (spec.horizontal()) look = look.multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-6) return false;
        Vec3 direction = look.normalize();
        Vec3 firstStep = direction.scale(spec.distance() / spec.ticks());
        // A dash pressed against a wall would only spend its cooldown.
        if (!level.noCollision(e, e.getBoundingBox().move(firstStep.x, Math.max(0, firstStep.y), firstStep.z))) {
            return false;
        }
        if (e instanceof Mob mob) mob.getNavigation().stop();
        DASHES.put(e, new Dash(e, level, spec, direction));
        push(e, DASHES.get(e));
        return true;
    }

    public static boolean isDashing(LivingEntity e) {
        return DASHES.containsKey(e);
    }

    /** Living tick: steer, strike, and end the dash. */
    public static void tick(LivingEntity e) {
        Dash dash = DASHES.get(e);
        if (dash == null) return;
        if (!e.isAlive() || e.level() != dash.level || e.isPassenger()) {
            DASHES.remove(e);
            return;
        }
        dash.travelled += e.position().distanceTo(dash.previous);
        dash.previous = e.position();
        boolean blocked = e.horizontalCollision && dash.ticksLeft < dash.spec.ticks();
        if (strike(e, dash) && dash.spec.stopOnHit()) blocked = true;
        dash.ticksLeft--;
        if (blocked || dash.ticksLeft <= 0 || dash.travelled >= dash.spec.distance()) {
            finish(e, dash);
            return;
        }
        push(e, dash);
    }

    private static void push(LivingEntity e, Dash dash) {
        Vec3 velocity = dash.direction.scale(dash.speed);
        if (dash.spec.horizontal()) {
            // Keep gravity's pull so a ground dash follows slopes and drops instead of floating.
            velocity = new Vec3(velocity.x, Math.min(0, e.getDeltaMovement().y), velocity.z);
        }
        e.setDeltaMovement(velocity);
        e.hasImpulse = true;
        e.fallDistance = 0f;
        if (e instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Run {@code on_hit} for creatures newly in the path; true when anything was struck. */
    private static boolean strike(LivingEntity e, Dash dash) {
        if (dash.spec.onHit() == null || dash.hit.size() >= dash.spec.maxHits()) return false;
        AABB path = e.getBoundingBox().expandTowards(dash.direction.scale(dash.speed)).inflate(dash.spec.hitRadius());
        List<LivingEntity> targets = dash.level.getEntitiesOfClass(LivingEntity.class, path,
                other -> other != e && other.isAlive() && !other.isSpectator() && !dash.hit.contains(other.getUUID())
                        && !Allies.areAllied(e, other));
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(e), b.distanceToSqr(e)));
        boolean struck = false;
        for (LivingEntity target : targets) {
            if (dash.hit.size() >= dash.spec.maxHits()) break;
            dash.hit.add(target.getUUID());
            // The struck creature is the focus; the dasher stays the origin and counterpart.
            dash.spec.onHit().run(new ActionContext(target, e, e));
            struck = true;
        }
        return struck;
    }

    private static void finish(LivingEntity e, Dash dash) {
        DASHES.remove(e);
        Vec3 m = e.getDeltaMovement();
        e.setDeltaMovement(m.x * 0.2, m.y, m.z * 0.2);
        e.hasImpulse = true;
        e.fallDistance = 0f;
        if (e instanceof ServerPlayer player) player.connection.send(new ClientboundSetEntityMotionPacket(player));
        if (dash.spec.onEnd() != null) dash.spec.onEnd().run(new ActionContext(e));
    }

    public static void clear() {
        DASHES.clear();
    }
}
