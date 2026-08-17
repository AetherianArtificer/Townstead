package com.aetherianartificer.townstead.chronicle.pregen;

import com.aetherianartificer.townstead.chronicle.template.ChronicleEventTemplate;
import com.aetherianartificer.townstead.chronicle.world.ChronicleWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Display params that no role carries, which live emission fills from the tap
 * and a fabricated past has to source itself. The source is a tag, so packs
 * decide the contents and the text comes from the item.
 */
public final class PregenParams {

    private PregenParams() {}

    /**
     * Resolves a template's param sources to concrete item ids. Null means one
     * source is empty in this world, which would leave a hole in the headline:
     * callers drop the template from the pool instead.
     */
    public static @Nullable Map<String, List<ResourceLocation>> resolve(
            ChronicleEventTemplate template, ChronicleWorld world) {
        if (template.pregenParams().isEmpty()) return Map.of();
        Map<String, List<ResourceLocation>> resolved = new HashMap<>();
        for (Map.Entry<String, ChronicleEventTemplate.PregenParamSource> source
                : template.pregenParams().entrySet()) {
            List<ResourceLocation> items = world.itemsInTag(source.getValue().itemTag());
            if (items.isEmpty()) return null;
            resolved.put(source.getKey(), items);
        }
        return resolved;
    }

    public static void fill(ChronicleWorld world, Map<String, String> params,
                            @Nullable Map<String, List<ResourceLocation>> pools, RandomSource rng) {
        if (pools == null) return;
        for (Map.Entry<String, List<ResourceLocation>> pool : pools.entrySet()) {
            if (params.containsKey(pool.getKey())) continue;
            List<ResourceLocation> items = pool.getValue();
            params.put(pool.getKey(), world.itemName(items.get(rng.nextInt(items.size()))));
        }
    }
}
