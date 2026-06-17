package com.aetherianartificer.townstead.pheno.selector.spatial;

import com.aetherianartificer.townstead.pheno.selector.Roles;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * A configurable ray: the parametric generalization of the {@code looking_at} place. Its origin is
 * the caster's eyes; its direction is the look vector by default, or an explicit {@code direction}
 * (in {@code world} or look-relative {@code local} space), or {@code toward} a role's entity; its
 * length is {@code distance} (default reach, or the gap to the {@code toward} entity). It resolves
 * the first block hit, and the first or all ({@code pierce}) entities along the line up to that
 * block. Shared by the {@code pheno:ray} entity and block selectors.
 */
public final class RayCast {

    private static final double DEFAULT_DISTANCE = 6.0;
    private static final double ENTITY_INFLATE = 0.3;

    private final double distance;
    private final boolean pierce;
    @Nullable private final double[] direction;
    private final boolean local;
    @Nullable private final Function<SelectorContext, LivingEntity> toward;

    private RayCast(double distance, boolean pierce, @Nullable double[] direction, boolean local,
                    @Nullable Function<SelectorContext, LivingEntity> toward) {
        this.distance = distance;
        this.pierce = pierce;
        this.direction = direction;
        this.local = local;
        this.toward = toward;
    }

    public static RayCast parse(JsonObject json) {
        double distance = GsonHelper.getAsDouble(json, "distance", 0);
        boolean pierce = GsonHelper.getAsBoolean(json, "pierce", false);
        double[] direction = json.has("direction") ? triple(json.get("direction")) : null;
        boolean local = "local".equalsIgnoreCase(GsonHelper.getAsString(json, "space", "world"));
        Function<SelectorContext, LivingEntity> toward = null;
        if (json.has("toward") && json.get("toward").isJsonPrimitive()) {
            String role = json.get("toward").getAsString();
            toward = ctx -> {
                List<LivingEntity> hit = Roles.resolve(role, ctx);
                return hit.isEmpty() ? null : hit.get(0);
            };
        }
        return new RayCast(distance, pierce, direction, local, toward);
    }

    /** The block this ray strikes first, or null when it reaches its end without hitting one. */
    @Nullable
    public BlockHitResult blockHit(SelectorContext ctx) {
        Ray ray = build(ctx);
        if (ray == null) return null;
        BlockHitResult hit = clip(ctx, ray, ray.end());
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /** The entities this ray strikes (the first, or all when piercing), nearest first, never past a wall. */
    public List<LivingEntity> entityHits(SelectorContext ctx) {
        Ray ray = build(ctx);
        if (ray == null) return List.of();
        Vec3 end = ray.end();
        BlockHitResult block = clip(ctx, ray, end);
        if (block.getType() == HitResult.Type.BLOCK) end = block.getLocation();
        Vec3 from = ray.origin;
        Vec3 to = end;
        AABB box = new AABB(from, to).inflate(ENTITY_INFLATE);
        LivingEntity self = ctx.self();
        List<LivingEntity> candidates = ctx.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != self && e.isPickable() && e.isAlive());
        List<Hit> hits = new ArrayList<>();
        for (LivingEntity e : candidates) {
            AABB hitBox = e.getBoundingBox().inflate(ENTITY_INFLATE);
            Optional<Vec3> clip = hitBox.clip(from, to);
            if (hitBox.contains(from)) {
                hits.add(new Hit(e, 0.0));
            } else if (clip.isPresent()) {
                hits.add(new Hit(e, from.distanceToSqr(clip.get())));
            }
        }
        if (hits.isEmpty()) return List.of();
        hits.sort(Comparator.comparingDouble(h -> h.distSqr));
        if (!pierce) return List.of(hits.get(0).entity);
        List<LivingEntity> out = new ArrayList<>(hits.size());
        for (Hit h : hits) out.add(h.entity);
        return out;
    }

    /** The impact point: the block hit, else the entity hit, else the ray's far end. */
    @Nullable
    public Vec3 impactPoint(SelectorContext ctx) {
        Ray ray = build(ctx);
        if (ray == null) return null;
        Vec3 end = ray.end();
        BlockHitResult block = clip(ctx, ray, end);
        if (block.getType() == HitResult.Type.BLOCK) return block.getLocation();
        List<LivingEntity> entities = entityHits(ctx);
        return entities.isEmpty() ? end : entities.get(0).getEyePosition();
    }

    private BlockHitResult clip(SelectorContext ctx, Ray ray, Vec3 end) {
        return ctx.level().clip(new ClipContext(ray.origin, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, ctx.self()));
    }

    @Nullable
    private Ray build(SelectorContext ctx) {
        LivingEntity self = ctx.self();
        if (self == null) return null;
        Vec3 origin = self.getEyePosition();
        double dist = distance > 0 ? distance : DEFAULT_DISTANCE;
        Vec3 dir;
        if (toward != null) {
            LivingEntity target = toward.apply(ctx);
            if (target == null) return null;
            Vec3 delta = target.getEyePosition().subtract(origin);
            double length = delta.length();
            if (length < 1.0e-4) return null;
            dir = delta.scale(1.0 / length);
            if (distance <= 0) dist = length;
        } else if (direction != null) {
            Vec3 raw = new Vec3(direction[0], direction[1], direction[2]);
            dir = local ? toLocal(self, raw) : raw.normalize();
        } else {
            dir = self.getViewVector(1.0f);
        }
        return new Ray(origin, dir, dist);
    }

    /** Rotate a look-relative vector (x right, y up, z forward) into world space. */
    private static Vec3 toLocal(LivingEntity self, Vec3 raw) {
        Vec3 forward = self.getViewVector(1.0f);
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(forward).normalize();
        return right.scale(raw.x).add(up.scale(raw.y)).add(forward.scale(raw.z)).normalize();
    }

    private static double[] triple(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            if (arr.size() >= 3) {
                return new double[]{arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble()};
            }
        }
        return new double[]{0, 0, 1};
    }

    private record Ray(Vec3 origin, Vec3 dir, double distance) {
        Vec3 end() {
            return origin.add(dir.scale(distance));
        }
    }

    private record Hit(LivingEntity entity, double distSqr) {}
}
