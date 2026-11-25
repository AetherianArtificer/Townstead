package com.aetherianartificer.townstead.storage;

import com.aetherianartificer.townstead.compat.mca.McaRoomBinding;
import com.aetherianartificer.townstead.work.site.Worksite;
import com.aetherianartificer.townstead.work.site.WorksiteKey;
import com.aetherianartificer.townstead.work.site.WorksiteRegister;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What the pathfinder asks about Ownership Plaques: whether a block lies inside a private place
 * that does not list this villager, so routes treat it as a wall and the villager stays out. A
 * villager already standing inside such a place may still walk out of it. Players and other mobs
 * are never stopped.
 */
public final class PrivatePaths {
    private PrivatePaths() {}

    private static final long PLACES_TICKS = 100;
    private static final long GATE_TICKS = 40;
    private static final int CACHE_LIMIT = 4096;

    private record Closed(List<Building> rooms, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean covers(int x, int y, int z, BlockPos pos) {
            if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return false;
            for (Building room : rooms) {
                if (room.containsPos(pos)) return true;
            }
            return false;
        }
    }

    private record Places(long at, List<RoomOwnershipAccess.Place> places) {}

    private static final class Gate {
        final long at;
        final List<Closed> closed;
        final Long2BooleanOpenHashMap answers = new Long2BooleanOpenHashMap();

        Gate(long at, List<Closed> closed) {
            this.at = at;
            this.closed = closed;
        }
    }

    private static final Map<ServerLevel, Places> PLACES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, Gate> GATES = Collections.synchronizedMap(new WeakHashMap<>());

    /** Whether standing at this block would put the villager inside a place closed to them. */
    public static boolean closed(Mob mob, int x, int y, int z) {
        if (!(mob instanceof VillagerEntityMCA villager) || !(mob.level() instanceof ServerLevel level)) return false;
        Gate gate = gateFor(level, villager);
        if (gate.closed.isEmpty()) return false;
        long key = BlockPos.asLong(x, y, z);
        if (gate.answers.containsKey(key)) return gate.answers.get(key);
        if (gate.answers.size() >= CACHE_LIMIT) gate.answers.clear();
        BlockPos pos = new BlockPos(x, y, z);
        boolean shut = false;
        for (Closed closed : gate.closed) {
            if (closed.covers(x, y, z, pos)) { shut = true; break; }
        }
        gate.answers.put(key, shut);
        return shut;
    }

    private static Gate gateFor(ServerLevel level, VillagerEntityMCA villager) {
        long now = level.getGameTime();
        Gate gate = GATES.get(villager);
        if (gate != null && now - gate.at < GATE_TICKS && now >= gate.at) return gate;
        List<Closed> closed = new ArrayList<>();
        BlockPos standing = villager.blockPosition();
        for (RoomOwnershipAccess.Place place : placesIn(level, now)) {
            if (place.lists(villager.getUUID(), villager)) continue;
            List<Building> rooms = place.rooms();
            if (rooms.isEmpty()) continue;
            boolean inside = false;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (Building room : rooms) {
                if (room.containsPos(standing)) { inside = true; break; }
                BlockPos a = room.getPos0();
                BlockPos b = room.getPos1();
                minX = Math.min(minX, Math.min(a.getX(), b.getX()));
                minY = Math.min(minY, Math.min(a.getY(), b.getY()));
                minZ = Math.min(minZ, Math.min(a.getZ(), b.getZ()));
                maxX = Math.max(maxX, Math.max(a.getX(), b.getX()));
                maxY = Math.max(maxY, Math.max(a.getY(), b.getY()));
                maxZ = Math.max(maxZ, Math.max(a.getZ(), b.getZ()));
            }
            // Someone already inside may walk out; the place closes behind them on the next refresh.
            if (inside) continue;
            closed.add(new Closed(rooms, minX, minY, minZ, maxX, maxY, maxZ));
        }
        Gate next = new Gate(now, closed);
        GATES.put(villager, next);
        return next;
    }

    private static List<RoomOwnershipAccess.Place> placesIn(ServerLevel level, long now) {
        Places cached = PLACES.get(level);
        if (cached != null && now - cached.at < PLACES_TICKS && now >= cached.at) return cached.places;
        List<RoomOwnershipAccess.Place> places = new ArrayList<>();
        if (level.getServer() != null) {
            Set<WorksiteKey> seen = new HashSet<>();
            for (Worksite site : WorksiteRegister.get(level.getServer()).all()) {
                if (site.ownershipTag() == null || !site.ownershipPrivate()
                        || !McaRoomBinding.ID.equals(site.key().binding())
                        || !site.key().dimension().equals(level.dimension().location())) continue;
                Building anchor = McaRoomBinding.byId(level, site.key());
                if (anchor == null) continue;
                RoomOwnershipAccess.Place place = RoomOwnershipAccess.placeOf(level, anchor);
                if (place == null || !place.restricts() || !seen.add(place.site().key())) continue;
                places.add(place);
            }
        }
        PLACES.put(level, new Places(now, List.copyOf(places)));
        return places;
    }
}
