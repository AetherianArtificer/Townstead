package com.aetherianartificer.townstead.chronicle.template;

import com.aetherianartificer.townstead.root.LegacyNamespace;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Server-authoritative registry of chronicle event templates, replaced
 * wholesale on data-pack (re)load. Never synced to clients: display text is
 * server-resolved.
 */
public final class ChronicleEventRegistry {

    private static volatile Map<ResourceLocation, ChronicleEventTemplate> ENTRIES = Map.of();

    private ChronicleEventRegistry() {}

    static void replaceAll(Map<ResourceLocation, ChronicleEventTemplate> entries) {
        ENTRIES = Map.copyOf(entries);
        ChronicleTriggerIndex.rebuild(ENTRIES.values());
    }

    public static @Nullable ChronicleEventTemplate byId(ResourceLocation id) {
        ChronicleEventTemplate template = ENTRIES.get(id);
        if (template != null) return template;
        return ENTRIES.get(LegacyNamespace.remap(id));
    }

    public static Map<ResourceLocation, ChronicleEventTemplate> all() {
        return ENTRIES;
    }

    public static boolean isEmpty() {
        return ENTRIES.isEmpty();
    }
}
