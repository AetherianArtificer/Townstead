package com.aetherianartificer.townstead.assign;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When each assignable is next usable, per entity.
 *
 * <p>ONE table for every provider. It began inside the ability layer keyed by gene id, which meant a
 * datapack action's declared cooldown was parsed, shipped to the wheel and drawn, and then enforced
 * by nobody: the only code that could record a cooldown only knew about genes. Keyed by assignable
 * id, the same table answers for all of them and the wheel's view needs no second lookup.</p>
 *
 * <p>Transient, and reset on reload, which is the same bargain the gene table always made: a
 * cooldown outliving a restart is not worth a save file.</p>
 */
public final class AssignCooldowns {

    private AssignCooldowns() {}

    private static final Map<UUID, Map<ResourceLocation, Long>> READY_AT = new ConcurrentHashMap<>();

    public static boolean isReady(LivingEntity entity, ResourceLocation id, long now) {
        return readyAt(entity, id) <= now;
    }

    /** When this is next usable, as a game time; 0 when it is ready now. */
    public static long readyAt(LivingEntity entity, ResourceLocation id) {
        if (entity == null || id == null) return 0L;
        Map<ResourceLocation, Long> map = READY_AT.get(entity.getUUID());
        return map == null ? 0L : map.getOrDefault(id, 0L);
    }

    public static void set(LivingEntity entity, ResourceLocation id, long readyAt) {
        if (entity == null || id == null) return;
        READY_AT.computeIfAbsent(entity.getUUID(), key -> new ConcurrentHashMap<>()).put(id, readyAt);
    }

    /** Starts a cooldown of {@code ticks} from now, or clears none when the action declares none. */
    public static void start(LivingEntity entity, ResourceLocation id, int ticks) {
        if (ticks <= 0) return;
        set(entity, id, entity.level().getGameTime() + ticks);
    }

    public static void clear(UUID uuid) {
        READY_AT.remove(uuid);
    }
}
