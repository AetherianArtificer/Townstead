package com.aetherianartificer.townstead.origin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * The player's own chosen origin id (the Destiny / self-edit selection).
 *
 * <p>The player is not a {@code VillagerEntityMCA}, so this is separate from
 * {@code TownsteadVillager.Life.originId}. It records the id only — applying a
 * genome to the player's appearance is out of scope. Persists across relog and
 * death (NeoForge attachment with {@code copyOnDeath}; Forge stores it in the
 * death-persistent player subtag).</p>
 */
public final class PlayerOrigin {

    private static final String ORIGIN_KEY = "originId";

    private PlayerOrigin() {}

    public static String getOriginId(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ORIGIN_DATA).getString(ORIGIN_KEY);
        //?} else {
        /*return persisted(player).getString(ORIGIN_KEY);
        *///?}
    }

    public static boolean hasOrigin(Player player) {
        return !getOriginId(player).isEmpty();
    }

    public static void setOriginId(Player player, String id) {
        String value = id == null ? "" : id;
        //? if neoforge {
        CompoundTag tag = player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ORIGIN_DATA);
        tag.putString(ORIGIN_KEY, value);
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ORIGIN_DATA, tag);
        //?} else {
        /*CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(Player.PERSISTED_NBT_TAG);
        persisted.putString(ORIGIN_KEY, value);
        root.put(Player.PERSISTED_NBT_TAG, persisted);
        *///?}
    }

    //? if forge {
    /*private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
    *///?}
}
