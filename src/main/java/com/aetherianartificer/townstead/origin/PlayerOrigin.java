package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.origin.gene.Genotype;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;

/**
 * The player's own chosen origin id (the Destiny / self-edit selection) plus the
 * diploid genotype rolled from it.
 *
 * <p>The player is not a {@code VillagerEntityMCA}, so this is separate from
 * {@code TownsteadVillager.Life}. We store the origin id and a genotype (rolled
 * once from the origin and kept stable so abilities/attachments don't reshuffle
 * each session and so the player can pass heritable alleles to children). Persists
 * across relog and death (NeoForge attachment with {@code copyOnDeath}; Forge
 * stores it in the death-persistent player subtag).</p>
 */
public final class PlayerOrigin {

    private static final String ORIGIN_KEY = "originId";
    private static final String GENOTYPE_KEY = "genotype";
    private static final String GRANTED_KEY = "grantedStarting";

    private PlayerOrigin() {}

    /** Whether the player has already received the starting kit for {@code geneId}. */
    public static boolean hasGrantedStarting(Player player, String geneId) {
        net.minecraft.nbt.ListTag list = data(player).getList(GRANTED_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(geneId)) return true;
        }
        return false;
    }

    public static void markGrantedStarting(Player player, String geneId) {
        CompoundTag tag = data(player);
        net.minecraft.nbt.ListTag list = tag.getList(GRANTED_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        list.add(net.minecraft.nbt.StringTag.valueOf(geneId));
        tag.put(GRANTED_KEY, list);
        store(player, tag);
    }

    public static String getOriginId(Player player) {
        return data(player).getString(ORIGIN_KEY);
    }

    public static boolean hasOrigin(Player player) {
        return !getOriginId(player).isEmpty();
    }

    /**
     * Set the player's origin id. When the id changes to a real origin, the stored
     * genotype is re-rolled from it so the player's expressed genes match the choice.
     */
    public static void setOriginId(Player player, String id) {
        String value = id == null ? "" : id;
        CompoundTag tag = data(player);
        boolean changed = !value.equals(tag.getString(ORIGIN_KEY));
        tag.putString(ORIGIN_KEY, value);
        if (changed) {
            ResourceLocation originId = ResourceLocation.tryParse(value);
            if (originId != null) {
                tag.put(GENOTYPE_KEY, Heredity.seedGenotype(originId, player.getRandom()).toTag());
            } else {
                tag.remove(GENOTYPE_KEY);
            }
        }
        store(player, tag);
    }

    /** The player's stored genotype (empty if none rolled yet). */
    public static Genotype getGenotype(Player player) {
        CompoundTag tag = data(player);
        return tag.contains(GENOTYPE_KEY) ? Genotype.fromTag(tag.getCompound(GENOTYPE_KEY)) : new Genotype();
    }

    public static void setGenotype(Player player, Genotype genotype) {
        CompoundTag tag = data(player);
        if (genotype == null || genotype.isEmpty()) tag.remove(GENOTYPE_KEY);
        else tag.put(GENOTYPE_KEY, genotype.toTag());
        store(player, tag);
    }

    /**
     * The player's genotype, seeding and persisting one from the current origin if
     * none exists yet. Server-side (mutates persisted state); used by the breeding
     * hook so a player parent contributes a stable set of alleles.
     */
    public static Genotype getOrSeedGenotype(Player player, RandomSource random) {
        Genotype stored = getGenotype(player);
        if (!stored.isEmpty()) return stored;
        ResourceLocation originId = ResourceLocation.tryParse(getOriginId(player));
        if (originId == null) originId = OriginRegistry.DEFAULT_ID;
        Genotype seeded = Heredity.seedGenotype(originId, random);
        setGenotype(player, seeded);
        return seeded;
    }

    private static CompoundTag data(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ORIGIN_DATA);
        //?} else {
        /*return persisted(player);
        *///?}
    }

    private static void store(Player player, CompoundTag tag) {
        //? if neoforge {
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ORIGIN_DATA, tag);
        //?} else {
        /*player.getPersistentData().put(Player.PERSISTED_NBT_TAG, tag);
        *///?}
    }

    //? if forge {
    /*private static CompoundTag persisted(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
    }
    *///?}
}
