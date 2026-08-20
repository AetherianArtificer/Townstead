package com.aetherianartificer.townstead.social;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The bond kinds a pack declared, replaced wholesale on data-pack (re)load.
 * An id nobody declared still works, behaving as {@link BondKind#fallback}, so
 * a template referring to a kind from an absent pack degrades rather than
 * breaking the history that mentions it.
 */
public final class BondKinds {

    private static volatile Map<ResourceLocation, BondKind> ENTRIES = Map.of();

    private BondKinds() {}

    public static void replaceAll(Map<ResourceLocation, BondKind> entries) {
        ENTRIES = Map.copyOf(entries);
    }

    public static BondKind byId(ResourceLocation id) {
        BondKind kind = ENTRIES.get(id);
        return kind != null ? kind : BondKind.fallback(id);
    }

    public static BondKind byId(String id) {
        ResourceLocation parsed = com.aetherianartificer.townstead.data.DataPackLang.parseId(id);
        return parsed == null ? BondKind.fallback(fallbackId(id)) : byId(parsed);
    }

    public static Map<ResourceLocation, BondKind> all() {
        return ENTRIES;
    }

    /** Kinds fed by an engine-provided relationship feed, e.g. {@code mca:marriage}. */
    public static List<BondKind> bySource(String source) {
        List<BondKind> matches = new ArrayList<>(1);
        for (BondKind kind : ENTRIES.values()) {
            if (source.equals(kind.source())) matches.add(kind);
        }
        return matches;
    }

    public static boolean isEmpty() {
        return ENTRIES.isEmpty();
    }

    private static ResourceLocation fallbackId(String raw) {
        ResourceLocation parsed = com.aetherianartificer.townstead.data.DataPackLang.parseId("townstead:unknown");
        return parsed;
    }
}
