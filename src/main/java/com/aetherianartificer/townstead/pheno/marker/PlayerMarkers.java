package com.aetherianartificer.townstead.pheno.marker;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Places a player has been sent to, by name: the ruin a story's map points at, for example.
 * Set by {@code pheno:mark_structure}, read by {@code pheno:near_marker} and by goal text.
 */
public final class PlayerMarkers {
    private static final String KEY = "townstead_markers";

    private PlayerMarkers() {}

    public static @Nullable GlobalPos get(Player player, String name) {
        CompoundTag markers = data(player).getCompound(KEY);
        if (!markers.contains(name)) return null;
        CompoundTag tag = markers.getCompound(name);
        ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        if (dimension == null) return null;
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }

    public static void set(Player player, String name, GlobalPos pos) {
        CompoundTag root = data(player);
        CompoundTag markers = root.getCompound(KEY);
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", pos.dimension().location().toString());
        tag.putInt("x", pos.pos().getX());
        tag.putInt("y", pos.pos().getY());
        tag.putInt("z", pos.pos().getZ());
        markers.put(name, tag);
        root.put(KEY, markers);
        store(player, root);
    }

    private static CompoundTag data(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA);
        //?} else {
        /*return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        *///?}
    }

    private static void store(Player player, CompoundTag root) {
        //? if neoforge {
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA, root);
        //?} else {
        /*player.getPersistentData().put(Player.PERSISTED_NBT_TAG, root);
        *///?}
    }
}
