package com.aetherianartificer.townstead.compat.travelerstitles;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCapsStore {
    private static final Set<UUID> HAS_TT =
            ConcurrentHashMap.newKeySet();

    private ClientCapsStore() {}

    public static void setTravelersTitles(UUID playerUuid, boolean value) {
        if (value) HAS_TT.add(playerUuid);
        else HAS_TT.remove(playerUuid);
    }

    public static boolean hasTravelersTitles(UUID playerUuid) {
        return HAS_TT.contains(playerUuid);
    }

    public static void clear(UUID playerUuid) {
        HAS_TT.remove(playerUuid);
    }
}
