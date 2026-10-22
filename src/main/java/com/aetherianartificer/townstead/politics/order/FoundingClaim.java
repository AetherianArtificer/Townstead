package com.aetherianartificer.townstead.politics.order;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A founding gift that founds its order when placed: the order kind and who will lead it, carried on
 * the item (an Oath Altar handed over by the person founding a hunters' lodge, say). The player who
 * was handed it keeps the claim too, so any of the gifts they place founds the order, a crafted one
 * included, until it is founded.
 */
public record FoundingClaim(ResourceLocation kind, UUID leader, String leaderName) {
    private static final String KEY = "townstead:founds";
    private static final String PENDING = "townstead:pending_foundings";

    public void mark(ItemStack stack) {
        CompoundTag claim = new CompoundTag();
        claim.putString("kind", kind.toString());
        claim.putUUID("leader", leader);
        claim.putString("leader_name", leaderName);
        //? if >=1.21 {
        CompoundTag tag = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        tag.put(KEY, claim);
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        //?} else {
        /*stack.getOrCreateTag().put(KEY, claim);
        *///?}
    }

    /** Keeps the claim on the player until the order is founded. */
    public void remember(net.minecraft.world.entity.player.Player player) {
        net.minecraft.nbt.ListTag list = player.getPersistentData().getList(PENDING, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.getCompound(i).getString("kind").equals(kind.toString())) list.remove(i);
        }
        CompoundTag claim = new CompoundTag();
        claim.putString("kind", kind.toString());
        claim.putUUID("leader", leader);
        claim.putString("leader_name", leaderName);
        list.add(claim);
        player.getPersistentData().put(PENDING, list);
    }

    /** The player's pending claim whose order's founding gives {@code gift}, or null. */
    public static @Nullable FoundingClaim pending(net.minecraft.world.entity.player.Player player, ResourceLocation gift) {
        net.minecraft.nbt.ListTag list = player.getPersistentData().getList(PENDING, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag claim = list.getCompound(i);
            ResourceLocation kind = ResourceLocation.tryParse(claim.getString("kind"));
            var definition = kind == null ? null
                    : com.aetherianartificer.townstead.politics.definition.PoliticalDefinitions.snapshot().kind(kind);
            if (definition != null && definition.founding().gifts().contains(gift) && claim.hasUUID("leader")) {
                return new FoundingClaim(kind, claim.getUUID("leader"), claim.getString("leader_name"));
            }
        }
        return null;
    }

    public static void forget(net.minecraft.world.entity.player.Player player, ResourceLocation kind) {
        net.minecraft.nbt.ListTag list = player.getPersistentData().getList(PENDING, net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.getCompound(i).getString("kind").equals(kind.toString())) list.remove(i);
        }
        player.getPersistentData().put(PENDING, list);
    }

    public static @Nullable FoundingClaim of(ItemStack stack) {
        //? if >=1.21 {
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        CompoundTag tag = data == null ? null : data.copyTag();
        //?} else {
        /*CompoundTag tag = stack.getTag();
        *///?}
        if (tag == null || !tag.contains(KEY)) return null;
        CompoundTag claim = tag.getCompound(KEY);
        ResourceLocation kind = ResourceLocation.tryParse(claim.getString("kind"));
        return kind == null || !claim.hasUUID("leader") ? null
                : new FoundingClaim(kind, claim.getUUID("leader"), claim.getString("leader_name"));
    }
}
