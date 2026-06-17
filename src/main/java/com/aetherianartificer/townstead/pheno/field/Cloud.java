package com.aetherianartificer.townstead.pheno.field;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionContext;
import com.aetherianartificer.townstead.pheno.condition.bientity.BiEntityCondition;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One lingering field (Apoli/Apugli effect cloud): a volume at a fixed point that, after a wait,
 * runs an entity action on each living entity inside it every reapplication cycle, for a duration,
 * growing or shrinking over time and on use. The action runs with the entity inside as
 * {@code entity()} and the owner as {@code other()}/{@code origin()}, so a one-shot effect or any
 * bi-entity action on the owner both fall out of it. Transient (gone on reload, like cooldowns and
 * resources); managed by {@link CloudManager}.
 */
public final class Cloud {

    private static final float MIN_RADIUS = 0.5f;
    private static final int CYCLE = 5;

    private final ServerLevel level;
    private final Vec3 center;
    @Nullable private final LivingEntity owner;
    private final Action action;
    @Nullable private final BiEntityCondition where;
    @Nullable private final ParticleOptions particle;
    private final float radiusPerTick;
    private final float radiusOnUse;
    private final int reapplyDelay;
    private final double height;
    private final Map<UUID, Long> victims = new HashMap<>();

    private float radius;
    private int waitRemaining;
    private int durationRemaining;

    public Cloud(ServerLevel level, Vec3 center, @Nullable LivingEntity owner, Action action,
                 @Nullable BiEntityCondition where, @Nullable ParticleOptions particle, float radius,
                 float radiusPerTick, float radiusOnUse, int waitTime, int duration, int reapplyDelay, double height) {
        this.level = level;
        this.center = center;
        this.owner = owner;
        this.action = action;
        this.where = where;
        this.particle = particle;
        this.radius = Math.max(MIN_RADIUS, radius);
        this.radiusPerTick = radiusPerTick;
        this.radiusOnUse = radiusOnUse;
        this.waitRemaining = Math.max(0, waitTime);
        this.durationRemaining = Math.max(1, duration);
        this.reapplyDelay = Math.max(1, reapplyDelay);
        this.height = height;
    }

    ServerLevel level() {
        return level;
    }

    /** Advance one tick; returns false when the field has dissipated and should be dropped. */
    boolean tick() {
        long now = level.getGameTime();
        if (waitRemaining > 0) {
            waitRemaining--;
            emitParticles();
            return true;
        }
        if (durationRemaining-- <= 0) return false;
        radius += radiusPerTick;
        if (radius < MIN_RADIUS) return false;
        emitParticles();
        return now % CYCLE != 0 || affect(now);
    }

    /** Apply to entities inside this cycle; returns false if a radius_on_use shrink dissipated it. */
    private boolean affect(long now) {
        AABB box = new AABB(center.x - radius, center.y, center.z - radius,
                center.x + radius, center.y + 1.0, center.z + radius).expandTowards(0.0, height, 0.0);
        LivingEntity own = owner != null && !owner.isRemoved() ? owner : null;
        List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != own && withinRadius(e));
        for (LivingEntity e : inside) {
            Long next = victims.get(e.getUUID());
            if (next != null && now < next) continue;
            if (where != null && own != null && !where.test(own, e)) continue;
            victims.put(e.getUUID(), now + reapplyDelay);
            action.run(own != null ? new ActionContext(e, own, own) : new ActionContext(e));
            if (radiusOnUse != 0.0f) {
                radius += radiusOnUse;
                if (radius < MIN_RADIUS) return false;
            }
        }
        victims.keySet().removeIf(id -> {
            Long expiry = victims.get(id);
            return expiry != null && now >= expiry && inside.stream().noneMatch(e -> e.getUUID().equals(id));
        });
        return true;
    }

    /** Horizontal radius check, matching the effect-cloud convention (vertical bounded by the box). */
    private boolean withinRadius(LivingEntity e) {
        double dx = e.getX() - center.x;
        double dz = e.getZ() - center.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private void emitParticles() {
        if (particle == null) return;
        int count = Math.max(1, (int) (radius * 2.0f));
        for (int i = 0; i < count; i++) {
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(level.random.nextDouble()) * radius;
            level.sendParticles(particle, center.x + Math.cos(angle) * dist, center.y + 0.1,
                    center.z + Math.sin(angle) * dist, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
