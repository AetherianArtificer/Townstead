package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** Data-pack-extensible catalog of bells allowed in a Charter Bell assembly. */
public final class CharterBellBlocks {
    public static final TagKey<Block> ELIGIBLE = TagKey.create(Registries.BLOCK,
            //? if >=1.21 {
            ResourceLocation.fromNamespaceAndPath("townstead", "charter_bells")
            //?} else {
            /*new ResourceLocation("townstead", "charter_bells")
            *///?}
    );
    private CharterBellBlocks() {}
}
