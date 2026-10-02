package com.aetherianartificer.townstead.politics.order;

import com.aetherianartificer.townstead.client.catalog.CatalogDataLoader;
import com.aetherianartificer.townstead.politics.state.PoliticalSavedData;
import com.aetherianartificer.townstead.politics.state.SettlementRef;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which gated building types a village has not unlocked: server-side, the ones whose order is not
 * based there; client-side, what the server last said, for the catalog.
 */
public final class VillageLocks {
    private static final Map<Integer, Set<String>> CLIENT = new ConcurrentHashMap<>();

    private VillageLocks() {}

    public static List<String> locked(ServerLevel level, Village village) {
        Map<String, ResourceLocation> gates = CatalogDataLoader.orderGates();
        if (gates.isEmpty()) return List.of();
        PoliticalSavedData data = PoliticalSavedData.get(level.getServer());
        SettlementRef settlement = new SettlementRef(level.dimension().location(), village.getId());
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, ResourceLocation> gate : gates.entrySet()) {
            if (Orders.at(data, settlement, gate.getValue()) == null) out.add(gate.getKey());
        }
        return out;
    }

    public static VillageLocksS2CPayload payload(ServerLevel level, Village village) {
        return new VillageLocksS2CPayload(village.getId(), locked(level, village));
    }

    /** Client: remembers what the server said for this village. */
    public static void accept(VillageLocksS2CPayload payload) {
        CLIENT.put(payload.villageId(), Set.copyOf(payload.locked()));
    }

    /** Client: whether the catalog should leave this building type out for this village. */
    public static boolean lockedOnClient(int villageId, String buildingType) {
        Set<String> locked = CLIENT.get(villageId);
        return locked != null && locked.contains(buildingType);
    }
}
