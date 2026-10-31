package com.aetherianartificer.townstead.persona;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Which Personas a player has met. A player bonds with each Persona once: founding another
 * village later does not bring a second copy of someone they already know.
 */
public final class PersonaBonds {
    private static final String KEY = "townstead_persona_bonds";

    private PersonaBonds() {}

    public static boolean has(Player player, ResourceLocation persona) {
        ListTag list = data(player).getList(KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(persona.toString())) return true;
        return false;
    }

    public static void add(Player player, ResourceLocation persona) {
        if (has(player, persona)) return;
        CompoundTag root = data(player);
        ListTag list = root.getList(KEY, Tag.TAG_STRING);
        list.add(StringTag.valueOf(persona.toString()));
        root.put(KEY, list);
        store(player, root);
    }

    public static boolean remove(Player player, ResourceLocation persona) {
        CompoundTag root = data(player);
        ListTag list = root.getList(KEY, Tag.TAG_STRING);
        boolean removed = list.removeIf(tag -> tag.getAsString().equals(persona.toString()));
        if (removed) {
            root.put(KEY, list);
            store(player, root);
        }
        return removed;
    }

    private static final String GIFTS_KEY = "townstead_persona_first_gifts";

    /** Whether the player has already given this Persona this gift once: a rule index, or a rule index and item. */
    public static boolean gaveFirst(Player player, ResourceLocation persona, String gift) {
        String key = persona + "#" + gift;
        ListTag list = data(player).getList(GIFTS_KEY, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) if (list.getString(i).equals(key)) return true;
        return false;
    }

    public static void markFirst(Player player, ResourceLocation persona, String gift) {
        if (gaveFirst(player, persona, gift)) return;
        CompoundTag root = data(player);
        ListTag list = root.getList(GIFTS_KEY, Tag.TAG_STRING);
        list.add(StringTag.valueOf(persona + "#" + gift));
        root.put(GIFTS_KEY, list);
        store(player, root);
    }

    public static void forgetGifts(Player player, ResourceLocation persona) {
        CompoundTag root = data(player);
        ListTag list = root.getList(GIFTS_KEY, Tag.TAG_STRING);
        if (list.removeIf(tag -> tag.getAsString().startsWith(persona + "#"))) {
            root.put(GIFTS_KEY, list);
            store(player, root);
        }
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
