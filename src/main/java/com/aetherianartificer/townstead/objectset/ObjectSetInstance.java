package com.aetherianartificer.townstead.objectset;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/** One recognised set in the world: which definition, where its anchor stands, and the blocks that satisfied it. */
public record ObjectSetInstance(ResourceLocation setId, BlockPos anchor, List<BlockPos> members) {

    public boolean involves(BlockPos pos) {
        if (anchor.equals(pos)) return true;
        for (BlockPos member : members) if (member.equals(pos)) return true;
        return false;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("set", setId.toString());
        tag.putLong("anchor", anchor.asLong());
        long[] packed = new long[members.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = members.get(i).asLong();
        tag.putLongArray("members", packed);
        return tag;
    }

    public static @Nullable ObjectSetInstance load(CompoundTag tag) {
        ResourceLocation setId = ResourceLocation.tryParse(tag.getString("set"));
        if (setId == null || !tag.contains("anchor")) return null;
        List<BlockPos> members = new ArrayList<>();
        for (long packed : tag.getLongArray("members")) members.add(BlockPos.of(packed));
        return new ObjectSetInstance(setId, BlockPos.of(tag.getLong("anchor")), List.copyOf(members));
    }
}
