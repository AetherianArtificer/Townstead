package com.aetherianartificer.townstead.pheno.power;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Powers an action gave an entity for a while ({@code pheno:grant_power}): "takes no new wound for 30 seconds", given
 * to someone else than the one whose skill it was. Server memory only, like timed flags, so a restart ends them. A
 * grant has an id, and granting the same id again renews it rather than stacking.
 */
public final class TimedPowers implements PowerSource {

    public static final TimedPowers INSTANCE = new TimedPowers();

    private record Grant(Power power, long until) {}

    private static final Map<UUID, Map<ResourceLocation, Grant>> GRANTS = new ConcurrentHashMap<>();

    private TimedPowers() {}

    /** Gives {@code entity} this power for {@code ticks}; 0 or less takes it away. */
    public static void grant(LivingEntity entity, ResourceLocation id, PowerComponent component, int ticks) {
        if (entity == null || id == null || entity.level().isClientSide()) return;
        if (ticks <= 0 || component == null) {
            revoke(entity, id);
            return;
        }
        if (GRANTS.size() > 4096) sweep(entity.level().getGameTime());
        GRANTS.computeIfAbsent(entity.getUUID(), key -> new ConcurrentHashMap<>())
                .put(id, new Grant(new Power(id, component), entity.level().getGameTime() + ticks));
        Powers.invalidate(entity);
    }

    public static void revoke(LivingEntity entity, ResourceLocation id) {
        Map<ResourceLocation, Grant> grants = GRANTS.get(entity.getUUID());
        if (grants == null || grants.remove(id) == null) return;
        if (grants.isEmpty()) GRANTS.remove(entity.getUUID());
        Powers.invalidate(entity);
    }

    /** Whether {@code entity} holds a timed power now; cheap enough for hot paths. */
    public static boolean has(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide()) return false;
        Map<ResourceLocation, Grant> grants = GRANTS.get(entity.getUUID());
        return grants != null && !grants.isEmpty();
    }

    @Override
    public boolean supports(LivingEntity entity) {
        return has(entity);
    }

    @Override
    public void collect(LivingEntity entity, List<Power> out) {
        if (entity.level().isClientSide()) return;
        Map<ResourceLocation, Grant> grants = GRANTS.get(entity.getUUID());
        if (grants == null) return;
        long now = entity.level().getGameTime();
        grants.values().removeIf(grant -> grant.until() <= now);
        if (grants.isEmpty()) {
            GRANTS.remove(entity.getUUID());
            return;
        }
        for (Grant grant : grants.values()) out.add(grant.power());
    }

    private static void sweep(long now) {
        GRANTS.values().forEach(grants -> grants.values().removeIf(grant -> grant.until() <= now));
        GRANTS.values().removeIf(Map::isEmpty);
    }

    public static void clearAll() {
        GRANTS.clear();
    }
}
