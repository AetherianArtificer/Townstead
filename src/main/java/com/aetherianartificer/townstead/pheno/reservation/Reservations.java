package com.aetherianartificer.townstead.pheno.reservation;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-local exclusive entity reservations. Runtime reservations are deliberately not saved. */
public final class Reservations {

    private static final class State {
        final Map<UUID, UUID> ownerByTarget = new HashMap<>();
        final Map<UUID, Set<UUID>> targetsByScope = new HashMap<>();
    }

    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();

    private Reservations() {}

    static boolean reserve(ReservationScope scope, LivingEntity target) {
        if (scope == null || target == null || !(target.level() instanceof ServerLevel level)
                || !target.isAlive()) return false;
        State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
        UUID existing = state.ownerByTarget.get(target.getUUID());
        if (existing != null && !existing.equals(scope.id())) return false;
        state.ownerByTarget.put(target.getUUID(), scope.id());
        state.targetsByScope.computeIfAbsent(scope.id(), ignored -> new LinkedHashSet<>())
                .add(target.getUUID());
        return true;
    }

    static List<LivingEntity> targets(ReservationScope scope, LivingEntity focus) {
        if (scope == null || focus == null || !(focus.level() instanceof ServerLevel level)) {
            return List.of();
        }
        State state = STATES.get(level.getServer());
        if (state == null) return List.of();
        Set<UUID> ids = state.targetsByScope.get(scope.id());
        if (ids == null || ids.isEmpty()) return List.of();
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : ids) {
            Entity entity = level.getEntity(id);
            if (entity instanceof LivingEntity living && living.isAlive()) out.add(living);
        }
        return out;
    }

    public static boolean isReserved(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return false;
        State state = STATES.get(level.getServer());
        return state != null && state.ownerByTarget.containsKey(entity.getUUID());
    }

    public static boolean isReservedByOther(ReservationScope scope, LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) return false;
        State state = STATES.get(level.getServer());
        if (state == null) return false;
        UUID owner = state.ownerByTarget.get(entity.getUUID());
        return owner != null && (scope == null || !owner.equals(scope.id()));
    }

    static void release(ReservationScope scope) {
        if (scope == null) return;
        for (State state : STATES.values()) {
            Set<UUID> targets = state.targetsByScope.remove(scope.id());
            if (targets == null) continue;
            for (UUID target : targets) state.ownerByTarget.remove(target, scope.id());
        }
    }

    public static void clear(MinecraftServer server) {
        if (server != null) STATES.remove(server);
    }
}
