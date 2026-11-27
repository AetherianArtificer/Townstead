package com.aetherianartificer.townstead.pheno.state;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived named flags on an entity: "for the next 10 seconds". Server memory only, never saved
 * and never synced, so they suit an effect window and nothing that must outlast a restart. An
 * entity state is the tool for anything with tiers, a body, or persistence.
 */
public final class TimedFlags {
    private static final Map<UUID, Map<ResourceLocation, Long>> FLAGS = new ConcurrentHashMap<>();

    private TimedFlags() {}

    public static void set(LivingEntity entity, ResourceLocation flag, int ticks) {
        if (ticks <= 0) {
            clear(entity, flag);
            return;
        }
        FLAGS.computeIfAbsent(entity.getUUID(), id -> new ConcurrentHashMap<>())
                .put(flag, entity.level().getGameTime() + ticks);
    }

    public static void clear(LivingEntity entity, ResourceLocation flag) {
        Map<ResourceLocation, Long> flags = FLAGS.get(entity.getUUID());
        if (flags == null) return;
        flags.remove(flag);
        if (flags.isEmpty()) FLAGS.remove(entity.getUUID());
    }

    public static boolean has(LivingEntity entity, ResourceLocation flag) {
        Map<ResourceLocation, Long> flags = FLAGS.get(entity.getUUID());
        if (flags == null) return false;
        Long until = flags.get(flag);
        if (until == null) return false;
        if (until > entity.level().getGameTime()) return true;
        clear(entity, flag);
        return false;
    }

    public static void clearAll() {
        FLAGS.clear();
    }
}
