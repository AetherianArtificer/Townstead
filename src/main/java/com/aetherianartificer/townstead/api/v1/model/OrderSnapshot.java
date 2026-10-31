package com.aetherianartificer.townstead.api.v1.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

/**
 * One production order on a worksite. {@code kind}, {@code mode}, {@code scope} and
 * {@code operation} are lowercase names of Townstead's own enums and may gain values.
 */
public record OrderSnapshot(
        long worksiteId,
        int index,
        ResourceLocation output,
        Optional<ResourceLocation> product,
        String productName,
        String kind,
        String mode,
        int target,
        String scope,
        boolean paused,
        Optional<ResourceLocation> profession,
        int minRank,
        Optional<UUID> villager,
        String operation
) {
    public OrderSnapshot {
        product = product == null ? Optional.empty() : product;
        profession = profession == null ? Optional.empty() : profession;
        villager = villager == null ? Optional.empty() : villager;
        productName = productName == null ? "" : productName;
    }
}
