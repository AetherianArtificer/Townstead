package com.aetherianartificer.townstead.pheno.selector;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;

import java.util.List;
import java.util.Locale;

/**
 * Resolves a role name to zero or one target within a {@link SelectorContext}. {@code self}/
 * {@code each} is the current focus, {@code origin} the fixed power-bearer, {@code attacker}/
 * {@code target} the contextual counterpart, plus the vanilla-resolvable relations
 * ({@code owner}/{@code vehicle}/{@code passenger}). Townstead-social roles (workplace/home/
 * village) resolve to empty until context providers land.
 */
public final class Roles {

    private Roles() {}

    public static List<LivingEntity> resolve(String role, SelectorContext ctx) {
        return switch (role.toLowerCase(Locale.ROOT)) {
            case "self", "each", "it" -> one(ctx.self());
            case "origin", "bearer", "actor" -> one(ctx.origin());
            case "attacker", "target", "other", "victim", "counterpart" -> one(ctx.other());
            case "owner" -> {
                // getOwner() returns LivingEntity on 1.20.1 but Entity on 1.21.1; widen to Entity for both.
                net.minecraft.world.entity.Entity owner = null;
                if (ctx.self() instanceof OwnableEntity ownable) owner = ownable.getOwner();
                yield owner instanceof LivingEntity living ? one(living) : List.of();
            }
            case "vehicle" -> ctx.self() != null && ctx.self().getVehicle() instanceof LivingEntity vehicle ? one(vehicle) : List.of();
            case "passenger" -> ctx.self() != null && ctx.self().getFirstPassenger() instanceof LivingEntity passenger ? one(passenger) : List.of();
            default -> List.of();
        };
    }

    public static boolean isRole(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "self", "each", "it", "origin", "bearer", "actor", "attacker", "target", "other",
                 "victim", "counterpart", "owner", "vehicle", "passenger" -> true;
            default -> false;
        };
    }

    private static List<LivingEntity> one(LivingEntity entity) {
        return entity == null ? List.of() : List.of(entity);
    }
}
