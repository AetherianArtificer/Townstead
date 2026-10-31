package com.aetherianartificer.townstead.social;

import com.aetherianartificer.townstead.data.DataPackLang;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/** Reload-time catalogue. Episodes retain their authored forgetting policy if a pack disappears. */
public final class SocialMemories {
    private static volatile Map<ResourceLocation, SocialMemoryDefinition> entries = Map.of();
    private SocialMemories() {}
    public static void replaceAll(Map<ResourceLocation, SocialMemoryDefinition> next) { entries = Map.copyOf(next); }
    public static Map<ResourceLocation, SocialMemoryDefinition> all() { return entries; }
    public static SocialMemoryDefinition byId(String raw) {
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) id = DataPackLang.parseId("townstead:unknown");
        return entries.getOrDefault(id, SocialMemoryDefinition.fallback(id));
    }
}
