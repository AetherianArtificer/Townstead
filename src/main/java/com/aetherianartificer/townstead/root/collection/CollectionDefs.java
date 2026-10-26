package com.aetherianartificer.townstead.root.collection;

import com.aetherianartificer.townstead.root.gene.types.CollectionGeneType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Collection stores declared outside a gene, in {@code data/<ns>/collection/}.
 *
 * <p>A collection is a store of accumulated per-holder state; being genetic is a
 * separate question. Declaring one as a gene forced every non-genetic system —
 * a social tie, a faction roll, a grudge — to invent a gene nobody inherits, so
 * the same config is declarable on its own. {@link CollectionValues} resolves a
 * gene first and falls back here, which leaves existing gene-declared
 * collections untouched.</p>
 */
public final class CollectionDefs {

    private static volatile Map<ResourceLocation, CollectionGeneType.Instance> ENTRIES = Map.of();

    private CollectionDefs() {}

    public static void replaceAll(Map<ResourceLocation, CollectionGeneType.Instance> entries) {
        ENTRIES = Map.copyOf(entries);
    }

    public static @Nullable CollectionGeneType.Instance byId(ResourceLocation id) {
        return ENTRIES.get(id);
    }

    public static Map<ResourceLocation, CollectionGeneType.Instance> all() {
        return ENTRIES;
    }
}
