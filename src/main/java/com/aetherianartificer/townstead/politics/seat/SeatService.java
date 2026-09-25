package com.aetherianartificer.townstead.politics.seat;

import com.aetherianartificer.townstead.compat.mca.McaBuildingCompat;
import com.aetherianartificer.townstead.politics.charter.CharterSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalActorRef;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.PoliticalStatus;
import com.aetherianartificer.townstead.politics.state.PolityInstance;
import com.aetherianartificer.townstead.politics.state.SeatInstance;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Designates, moves, and checks Seats. A founded Charter lectern inside a recognized MCA building
 * makes that building the Seat of the polity the Charter speaks for.
 */
public final class SeatService {
    /** Damage: the Seat keeps its place and is repaired by rebuilding what broke. */
    public static final String BUILDING_MISSING = "building_missing";
    public static final String LECTERN_MISSING = "lectern_missing";
    /** Loss: the Seat record ends. */
    public static final String CHARTER_REMOVED = "charter_removed";
    public static final String ACTOR_DISSOLVED = "actor_dissolved";

    private static final int CHECK_INTERVAL = 100;
    // A Seat is damaged only after two failed checks in a row, so a room rescan cannot flicker it.
    private static final int STRIKES_TO_DAMAGE = 2;
    private static final Map<PoliticalActorRef, Integer> STRIKES = new HashMap<>();

    public enum Result { DESIGNATED, MOVED, ALREADY_HERE, SEAT_ELSEWHERE, NO_BUILDING, NO_POLITY }

    private SeatService() {}

    /** With {@code allowMove} false, an actor that already has a Seat elsewhere keeps it. */
    public static Result designate(ServerLevel level, CharterSavedData.Binding binding, boolean allowMove) {
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        PolityInstance polity = data.polity(binding.polity());
        if (polity == null || polity.status() == PoliticalStatus.Polity.DISSOLVED) return Result.NO_POLITY;
        Building building = host(level, binding);
        if (building == null) return Result.NO_BUILDING;
        SeatInstance existing = data.seat(polity.actor());
        SeatInstance candidate = new SeatInstance(polity.actor(), binding.settlement(), binding.lectern(),
                building.getId(), level.getGameTime());
        if (candidate.sameHost(existing)) return Result.ALREADY_HERE;
        if (existing != null && !allowMove) return Result.SEAT_ELSEWHERE;
        data.putSeat(candidate);
        return existing == null ? Result.DESIGNATED : Result.MOVED;
    }

    /**
     * The MCA building that holds this Charter: the room around the lectern, or else the building
     * the bell belongs to. A Charter Bell on a square is part of MCA's town center, so an
     * outdoor Charter still has a Seat. Null when neither stands in a building.
     */
    public static @Nullable Building host(ServerLevel level, CharterSavedData.Binding binding) {
        Village village = VillageManager.get(level).getOrEmpty(binding.settlement().villageId()).orElse(null);
        if (village == null) return null;
        Building room = McaBuildingCompat.buildingAt(level, village, binding.lectern());
        return room != null ? room : McaBuildingCompat.buildingAt(level, village, binding.bell());
    }

    public static void tick(MinecraftServer server) {
        SeatNotices.send(server);
        if (server.getTickCount() % CHECK_INTERVAL != 0) return;
        PoliticalSavedData data = PoliticalSavedData.get(server);
        CharterSavedData charters = CharterSavedData.get(server);
        for (SeatInstance seat : data.seats()) {
            String failure = check(server, data, charters, seat);
            if (failure == null) {
                STRIKES.remove(seat.actor());
                SeatInstance current = data.seat(seat.actor());
                if (current != null && current.damaged()) data.putSeat(current.repaired());
                continue;
            }
            if (failure.isEmpty()) continue;
            if (failure.equals(ACTOR_DISSOLVED) || failure.equals(CHARTER_REMOVED)) {
                STRIKES.remove(seat.actor());
                data.removeSeat(seat.actor(), failure);
            } else if (failure.equals(seat.damage())) {
                STRIKES.remove(seat.actor());
            } else if (STRIKES.merge(seat.actor(), 1, Integer::sum) >= STRIKES_TO_DAMAGE) {
                STRIKES.remove(seat.actor());
                data.putSeat(seat.withDamage(failure, server.overworld().getGameTime()));
            }
        }
    }

    public static void clear() {
        STRIKES.clear();
        SeatNotices.clear();
    }

    /** Null when the Seat is intact, empty when it cannot be checked now, else the reason it failed. */
    private static @Nullable String check(MinecraftServer server, PoliticalSavedData data,
                                          CharterSavedData charters, SeatInstance seat) {
        if (seat.actor().kind() == PoliticalActorRef.Kind.POLITY) {
            PolityInstance polity = data.polity(seat.actor().id());
            if (polity == null || polity.status() == PoliticalStatus.Polity.DISSOLVED) return ACTOR_DISSOLVED;
        }
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, seat.settlement().dimension()));
        if (level == null || !level.isLoaded(seat.lectern())) return "";
        CharterSavedData.Binding binding = charters.binding(seat.settlement().dimension(), seat.lectern());
        if (binding == null || !binding.polity().equals(seat.actor().id())) return CHARTER_REMOVED;
        if (!level.getBlockState(seat.lectern()).is(Blocks.LECTERN)) return LECTERN_MISSING;
        Building building = host(level, binding);
        if (building == null) return BUILDING_MISSING;
        if (building.getId() != seat.buildingId()) data.putSeat(seat.withBuilding(building.getId()));
        return null;
    }
}
