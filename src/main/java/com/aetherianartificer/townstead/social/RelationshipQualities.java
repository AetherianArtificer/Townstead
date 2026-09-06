package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Immutable reload-time catalogue. Saved contributions retain their ids when a pack disappears. */
public final class RelationshipQualities {
    public static final String AFFECTION = "townstead:affection";
    private static volatile Map<ResourceLocation, RelationshipQuality> entries = Map.of();
    private RelationshipQualities() {}
    public static void replaceAll(Map<ResourceLocation, RelationshipQuality> next) { entries = Map.copyOf(next); }
    public static Map<ResourceLocation, RelationshipQuality> all() { return entries; }
    public static RelationshipQuality byId(String raw) {
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) id = DataPackLang.parseId("townstead:unknown");
        return entries.getOrDefault(id, RelationshipQuality.fallback(id));
    }
}
