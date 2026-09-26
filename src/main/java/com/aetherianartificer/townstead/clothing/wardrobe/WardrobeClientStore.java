package com.aetherianartificer.townstead.clothing.wardrobe;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Client mirror of the wardrobe grid and the template list, filled by the sync payload. */
public final class WardrobeClientStore {

    private static volatile List<WardrobeTemplate> templates = List.of();
    private static volatile List<String> village = List.of();
    private static volatile Map<UUID, List<String>> villagers = Map.of();

    private WardrobeClientStore() {}

    public static void set(WardrobeSyncPayload payload) {
        if (payload == null) return;
        templates = List.copyOf(payload.templates());
        village = List.copyOf(payload.village());
        villagers = Map.copyOf(new LinkedHashMap<>(payload.villagers()));
    }

    public static List<WardrobeTemplate> templates() {
        return templates;
    }

    public static @Nullable WardrobeTemplate template(@Nullable String id) {
        if (id == null || id.isEmpty()) return null;
        for (WardrobeTemplate template : templates) {
            if (id.equals(template.id().toString())) return template;
        }
        return null;
    }

    public static @Nullable WardrobeTemplate template(@Nullable ResourceLocation id) {
        return id == null ? null : template(id.toString());
    }

    public static String village(int day) {
        return day >= 0 && day < village.size() ? village.get(day) : "";
    }

    public static String villager(@Nullable UUID uuid, int day) {
        if (uuid == null) return "";
        List<String> row = villagers.get(uuid);
        return row != null && day >= 0 && day < row.size() ? row.get(day) : "";
    }

    public static void clear() {
        templates = List.of();
        village = List.of();
        villagers = Map.of();
    }
}
