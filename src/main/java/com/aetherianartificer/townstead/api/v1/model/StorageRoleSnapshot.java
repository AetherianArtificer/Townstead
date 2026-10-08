package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/** A storage role definition: which blocks count as which kind of storage. {@code role} is a lowercase role name. */
public record StorageRoleSnapshot(
        ResourceLocation id,
        String role,
        Set<ResourceLocation> blocks,
        List<ResourceLocation> blockTags,
        Set<String> namespaces
) {
    public StorageRoleSnapshot {
        blocks = blocks == null ? Set.of() : Set.copyOf(blocks);
        blockTags = blockTags == null ? List.of() : List.copyOf(blockTags);
        namespaces = namespaces == null ? Set.of() : Set.copyOf(namespaces);
    }
}
