package com.aetherianartificer.townstead.clothing.wardrobe;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client mirror of the Wardrobe, filled by the sync payload. */
public final class WardrobeClientStore {

    private static volatile List<WardrobeTemplate> templates = List.of();
    private static volatile boolean villageWarm = true;
    private static volatile boolean villageLight = true;
    private static volatile Map<UUID, WardrobeSyncPayload.Resident> residents = Map.of();

    private WardrobeClientStore() {}

    public static void set(WardrobeSyncPayload payload) {
        if (payload == null) return;
        templates = List.copyOf(payload.templates());
        villageWarm = payload.villageWarm();
        villageLight = payload.villageLight();
        residents = Map.copyOf(new LinkedHashMap<>(payload.residents()));
    }

    public static @Nullable WardrobeTemplate template(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        for (WardrobeTemplate template : templates) {
            if (id.equals(template.id().toString())) return template;
        }
        return null;
    }

    public static boolean villageWarm() {
        return villageWarm;
    }

    public static boolean villageLight() {
        return villageLight;
    }

    public static @Nullable WardrobeSyncPayload.Resident resident(@Nullable UUID uuid) {
        return uuid == null ? null : residents.get(uuid);
    }

    public static String villager(@Nullable UUID uuid, int day) {
        WardrobeSyncPayload.Resident resident = resident(uuid);
        if (resident == null) return "";
        List<String> row = resident.row();
        return day >= 0 && day < row.size() ? row.get(day) : "";
    }

    public static String work(@Nullable UUID uuid) {
        WardrobeSyncPayload.Resident resident = resident(uuid);
        return resident == null ? "" : resident.work();
    }

    /** Whether the villager's warm layers (true) or light layers (false) end up on. */
    public static boolean layersOn(@Nullable UUID uuid, boolean warmLayer) {
        WardrobeSyncPayload.Resident resident = resident(uuid);
        byte state = resident == null ? WardrobeAssignments.INHERIT : warmLayer ? resident.warm() : resident.light();
        if (state == WardrobeAssignments.ON) return true;
        if (state == WardrobeAssignments.OFF) return false;
        return warmLayer ? villageWarm : villageLight;
    }

    public static void clear() {
        templates = List.of();
        villageWarm = true;
        villageLight = true;
        residents = Map.of();
    }
}
