package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.TeleportSupport;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.Roles;
import com.aetherianartificer.townstead.pheno.selector.Selector;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.selector.Selectors;
import com.aetherianartificer.townstead.pheno.selector.spatial.Region;
import com.aetherianartificer.townstead.pheno.selector.spatial.Spatial;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Composable entity teleport. {@code on} (handled by {@link com.aetherianartificer.townstead.pheno.action.Actions})
 * chooses what moves; {@code to} chooses a place, role, or entity selection. An offset may be in
 * world or look-relative space and an optional random profile scatters or searches around the
 * resulting destination.
 *
 * <p>Examples:
 * <pre>{@code
 * { "type":"pheno:teleport", "offset":[0,0,10], "space":"local" }
 * { "type":"pheno:teleport", "on":{"around":"here","radius":4},
 *   "to":{"at":[100,64,100]}, "preserve_offset_from":"origin" }
 * { "type":"pheno:teleport", "to":"target",
 *   "random":{"radius":[8,3,8],"min_distance":2,"shape":"cylinder","attempts":24} }
 * }</pre>
 */
public final class TeleportActionType implements ActionType {

    public static final String KEY = "pheno:teleport";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        Destination destination = json.has("to") ? destination(json.get("to")) : Destination.SELF;
        if (destination == null) return null;
        Destination formationOrigin = json.has("preserve_offset_from")
                ? destination(json.get("preserve_offset_from")) : null;
        if (json.has("preserve_offset_from") && formationOrigin == null) return null;

        Vec3 offset = vector(json.get("offset"), Vec3.ZERO);
        boolean local = switch (GsonHelper.getAsString(json, "space", "world").toLowerCase(Locale.ROOT)) {
            case "local", "relative", "facing" -> true;
            default -> GsonHelper.getAsBoolean(json, "relative", false);
        };
        RandomProfile random = RandomProfile.parse(json.get("random"));
        boolean randomDisabled = json.has("random") && json.get("random").isJsonPrimitive()
                && json.getAsJsonPrimitive("random").isBoolean() && !json.get("random").getAsBoolean();
        if (json.has("random") && random == null && !randomDisabled) return null;
        boolean safe = GsonHelper.getAsBoolean(json, "safe", random != null);
        boolean resetVelocity = GsonHelper.getAsBoolean(json, "reset_velocity", false);

        Destination to = destination;
        Destination from = formationOrigin;
        return ctx -> {
            LivingEntity entity = ctx.entity();
            SelectorContext sc = SelectorContext.of(ctx);
            Vec3 target = to.resolve(sc);
            if (target == null) {
                ctx.fail();
                return;
            }

            if (from != null) {
                Vec3 anchor = from.resolve(sc);
                if (anchor == null) {
                    ctx.fail();
                    return;
                }
                target = target.add(entity.position().subtract(anchor));
            }
            Vec3 appliedOffset = local ? local(offset, entity) : offset;
            target = target.add(appliedOffset);

            boolean moved = random == null
                    ? TeleportSupport.teleport(entity, target, safe)
                    : random.teleport(entity, target, safe);
            if (!moved) {
                ctx.fail();
                return;
            }
            if (moved && resetVelocity) entity.setDeltaMovement(Vec3.ZERO);
        };
    }

    /** Local axes are x=right, y=up, z=forward; pitch is deliberately ignored. */
    private static Vec3 local(Vec3 value, LivingEntity entity) {
        return value.yRot((float) -Math.toRadians(entity.getYRot()));
    }

    @Nullable
    private static Destination destination(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        Region spatial = Spatial.parse(value);
        if (spatial != null) return ctx -> center(spatial.anchor(ctx));

        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String role = value.getAsString();
            if (!Roles.isRole(role)) return null;
            return ctx -> firstPosition(Roles.resolve(role, ctx));
        }

        // {"entity": <normal Pheno selector>} makes the intent explicit. A typed selector is
        // also accepted directly, making command and collection destinations compose naturally.
        JsonElement selected = value;
        if (value.isJsonObject() && value.getAsJsonObject().has("entity")) {
            selected = value.getAsJsonObject().get("entity");
        }
        Selector selector = Selectors.parse(selected);
        return selector == null ? null : ctx -> firstPosition(selector.select(ctx));
    }

    @Nullable
    private static Vec3 firstPosition(List<LivingEntity> entities) {
        return entities.isEmpty() ? null : entities.get(0).position();
    }

    @Nullable
    private static Vec3 center(@Nullable BlockPos pos) {
        // Spell this out rather than Vec3.atBottomCenterOf(Vec3i): the lightweight unit-test
        // BlockPos stand-in intentionally does not inherit Minecraft's full Vec3i hierarchy.
        return pos == null ? null : new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static Vec3 vector(@Nullable JsonElement value, Vec3 fallback) {
        if (value == null || !value.isJsonArray()) return fallback;
        JsonArray a = value.getAsJsonArray();
        return a.size() < 3 ? fallback : new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    @FunctionalInterface
    private interface Destination {
        Destination SELF = ctx -> ctx.self() == null ? ctx.pos() : ctx.self().position();
        @Nullable Vec3 resolve(SelectorContext ctx);
    }

    private record RandomProfile(Vec3 radius, double minDistance, Shape shape, int attempts) {

        @Nullable
        static RandomProfile parse(@Nullable JsonElement value) {
            if (value == null || value.isJsonNull()) return null;
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isBoolean()) {
                    return value.getAsBoolean() ? new RandomProfile(new Vec3(8, 8, 8), 0, Shape.BOX, 16) : null;
                }
                double r = Math.max(0, value.getAsDouble());
                return new RandomProfile(new Vec3(r, r, r), 0, Shape.BOX, 16);
            }
            if (value.isJsonArray()) {
                Vec3 r = positive(vector(value, Vec3.ZERO));
                return new RandomProfile(r, 0, Shape.BOX, 16);
            }
            if (!value.isJsonObject()) return null;
            JsonObject obj = value.getAsJsonObject();
            Vec3 radius = radius(obj.get("radius"), 8);
            double min = Math.max(0, GsonHelper.getAsDouble(obj, "min_distance", 0));
            Shape shape = Shape.parse(GsonHelper.getAsString(obj, "shape", "box"));
            int attempts = Math.max(1, GsonHelper.getAsInt(obj, "attempts", 16));
            return new RandomProfile(radius, min, shape, attempts);
        }

        boolean teleport(LivingEntity entity, Vec3 center, boolean safe) {
            RandomSource rng = entity.getRandom();
            for (int i = 0; i < attempts; i++) {
                Vec3 delta = sample(rng);
                if (delta.lengthSqr() < minDistance * minDistance) continue;
                if (TeleportSupport.teleport(entity, center.add(delta), safe)) return true;
            }
            return false;
        }

        private Vec3 sample(RandomSource rng) {
            if (shape == Shape.BOX) return new Vec3(axis(rng, radius.x), axis(rng, radius.y), axis(rng, radius.z));
            double angle = rng.nextDouble() * Math.PI * 2;
            double radial = Math.sqrt(rng.nextDouble()); // uniform area, not center-biased
            double x = Math.cos(angle) * radius.x * radial;
            double z = Math.sin(angle) * radius.z * radial;
            double y = axis(rng, radius.y);
            if (shape == Shape.SPHERE) {
                double yNorm = radius.y == 0 ? 0 : y / radius.y;
                double horizontal = Math.sqrt(Math.max(0, 1 - yNorm * yNorm));
                x *= horizontal;
                z *= horizontal;
            }
            return new Vec3(x, y, z);
        }

        private static double axis(RandomSource rng, double extent) {
            return (rng.nextDouble() * 2 - 1) * extent;
        }

        private static Vec3 radius(@Nullable JsonElement value, double fallback) {
            if (value == null) return new Vec3(fallback, fallback, fallback);
            if (value.isJsonArray()) return positive(vector(value, Vec3.ZERO));
            double r = Math.max(0, value.getAsDouble());
            return new Vec3(r, r, r);
        }

        private static Vec3 positive(Vec3 v) {
            return new Vec3(Math.max(0, v.x), Math.max(0, v.y), Math.max(0, v.z));
        }
    }

    private enum Shape {
        BOX, CYLINDER, SPHERE;

        static Shape parse(String raw) {
            return switch (raw.toLowerCase(Locale.ROOT)) {
                case "sphere", "ellipsoid" -> SPHERE;
                case "cylinder", "disc", "disk" -> CYLINDER;
                default -> BOX;
            };
        }
    }
}
