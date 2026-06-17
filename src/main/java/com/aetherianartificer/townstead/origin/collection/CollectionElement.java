package com.aetherianartificer.townstead.origin.collection;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Encodes a collection member to its canonical string and back, so one string-keyed store serves
 * every {@code of}: an entity is its UUID, a block its packed position, an item its registry id, a
 * key the literal string. Only entity and block decode back to a live target (for the collection
 * selector); item and key collections are membership stores read by size/contains.
 */
public final class CollectionElement {

    private CollectionElement() {}

    public static String ofEntity(LivingEntity entity) {
        return entity.getUUID().toString();
    }

    public static String ofBlock(BlockPos pos) {
        return Long.toString(pos.asLong());
    }

    public static String ofItem(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    @Nullable
    public static UUID toUuid(String element) {
        try {
            return UUID.fromString(element);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static BlockPos toBlock(String element) {
        try {
            return BlockPos.of(Long.parseLong(element));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
