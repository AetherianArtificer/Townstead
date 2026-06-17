package com.aetherianartificer.townstead.origin.rig;

import java.util.List;
import java.util.Map;

/**
 * Server-side store of loaded {@link RigDefinition}s, keyed by id, filled by {@link RigJsonLoader}.
 * {@code OriginCatalog} reads {@link #all()} to ship the definitions to clients in the origin catalog
 * sync, so a connecting client renders custom rigs without needing the data pack itself. The client's
 * copy lives in {@code OriginCatalogClient}. Rigs are pack content: a species references a rig by the
 * id of a {@code data/<ns>/rigs/*.json} it (or another loaded pack) provides; the mod ships none.
 */
public final class RigRegistry {

    private static volatile Map<String, RigDefinition> RIGS = Map.of();

    private RigRegistry() {}

    public static void replaceAll(Map<String, RigDefinition> next) {
        RIGS = Map.copyOf(next);
    }

    /** Every loaded rig definition, for the origin-catalog sync. */
    public static List<RigDefinition> all() {
        return List.copyOf(RIGS.values());
    }

    /** The rig definition for an id, or null if unknown. */
    public static RigDefinition byId(String id) {
        return resolve(RIGS, id);
    }

    /** Shared lookup, reused by the client store. */
    public static RigDefinition resolve(Map<String, RigDefinition> rigs, String id) {
        return (id == null || id.isEmpty()) ? null : rigs.get(id);
    }
}
