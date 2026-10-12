package com.aetherianartificer.townstead.root;

import com.aetherianartificer.townstead.root.gene.Genotype;
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
public final class PlayerRoot {

    private static final String ROOT_KEY = "rootId";
    private static final String LEGACY_ROOT_KEY = "originId";
    private static final String GENOTYPE_KEY = "genotype";
    private static final String GRANTED_KEY = "grantedStarting";
    /** A player's carried heritage, when it isn't simply their root's (one who took over a descendant). */
    private static final String HERITAGE_KEY = "heritage";
    /** The browser mix the player was founded from; any new root or heritage drops it. */
    private static final String MIX_KEY = "rootMix";

    private PlayerRoot() {}

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

    public static String getRootId(Player player) {
        CompoundTag tag = data(player);
        return tag.contains(ROOT_KEY) ? tag.getString(ROOT_KEY) : tag.getString(LEGACY_ROOT_KEY); // legacy fallback
    }

    public static boolean hasRoot(Player player) {
        return !getRootId(player).isEmpty();
    }

    /**
     * Set the player's origin id. When the id changes to a real origin, the stored
     * genotype is re-rolled from it so the player's expressed genes match the choice.
     */
    public static void setRootId(Player player, String id) {
        String value = id == null ? "" : id;
        CompoundTag tag = data(player);
        boolean changed = !value.equals(tag.getString(ROOT_KEY)) || tag.contains(MIX_KEY);
        tag.putString(ROOT_KEY, value);
        if (changed) {
            tag.remove(HERITAGE_KEY);          // a fresh founder of the new root: its own pure heritage
            tag.remove(MIX_KEY);
            ResourceLocation rootId = ResourceLocation.tryParse(value);
            if (rootId != null) {
                tag.put(GENOTYPE_KEY, Heredity.seedGenotype(rootId, player.getRandom()).toTag());
            } else {
                tag.remove(GENOTYPE_KEY);
            }
        }
        store(player, tag);
        // Root selection changes physical dimensions on the authoritative server too.
        if (changed && player.getAttributes() != null) player.refreshDimensions();
    }

    /**
     * Become {@code rootId} carrying a given genotype and heritage instead of a fresh roll: a player
     * taking over a descendant keeps that descendant's genes and blood (a half-elf stays half-elf).
     */
    public static void adopt(Player player, String rootId, Genotype genotype,
                             @org.jetbrains.annotations.Nullable Heritage heritage) {
        CompoundTag tag = data(player);
        tag.putString(ROOT_KEY, rootId == null ? "" : rootId);
        tag.put(GENOTYPE_KEY, genotype.toTag());
        if (heritage != null && !heritage.isEmpty()) tag.put(HERITAGE_KEY, heritage.toTag());
        else tag.remove(HERITAGE_KEY);
        tag.remove(MIX_KEY);
        store(player, tag);
        if (player.getAttributes() != null) player.refreshDimensions();
    }

    /** The mix the player was founded from in the root browser, or null. */
    @org.jetbrains.annotations.Nullable
    public static RootMix getMix(Player player) {
        return RootMix.fromTag(data(player).getList(MIX_KEY, net.minecraft.nbt.Tag.TAG_COMPOUND));
    }

    public static void setMix(Player player, RootMix mix) {
        CompoundTag tag = data(player);
        tag.put(MIX_KEY, mix.toTag());
        store(player, tag);
    }

    /** The heritage the player carries: an adopted one, else their root's own. */
    public static Heritage getHeritage(Player player) {
        CompoundTag tag = data(player);
        if (tag.contains(HERITAGE_KEY)) {
            Heritage stored = Heritage.fromTag(tag.getCompound(HERITAGE_KEY));
            if (stored != null && !stored.isEmpty()) return stored;
        }
        ResourceLocation rootId = ResourceLocation.tryParse(getRootId(player));
        return RootRegistry.seedHeritage(rootId == null ? RootRegistry.DEFAULT_ID : rootId);
    }

    /** The player's stored genotype (empty if none rolled yet). */
    public static Genotype getGenotype(Player player) {
        CompoundTag tag = data(player);
        if (!tag.contains(GENOTYPE_KEY)) return new Genotype();
        Genotype genotype = Genotype.fromTag(tag.getCompound(GENOTYPE_KEY));
        // Players have no load-time migrate pass, so stale private-slot entries (a gene
        // that has since gained a shared locus) are relocated here and persisted.
        if (Heredity.canonicalizeLoci(genotype)) setGenotype(player, genotype);
        return genotype;
    }

    public static void setGenotype(Player player, Genotype genotype) {
        CompoundTag tag = data(player);
        if (genotype == null || genotype.isEmpty()) tag.remove(GENOTYPE_KEY);
        else tag.put(GENOTYPE_KEY, genotype.toTag());
        com.aetherianartificer.townstead.pheno.power.Powers.invalidate(player);
        store(player, tag);
    }

    /**
     * Top up the player's genotype with genes their root has gained since it was rolled (a pack
     * update adding horns, say). Villagers get the same on load; players have no load pass, so
     * this runs whenever their root data is sent: login and every data-pack reload. Server-side.
     * Returns whether anything was added.
     */
    public static boolean fillMissingGenes(Player player) {
        ResourceLocation rootId = ResourceLocation.tryParse(getRootId(player));
        if (rootId == null) return false;
        Genotype genotype = getGenotype(player);
        if (genotype.isEmpty()) return false;
        if (!Heredity.fillMissing(genotype, rootId, player.getRandom(), null)) return false;
        setGenotype(player, genotype);
        return true;
    }

    /**
     * The player's genotype, seeding and persisting one from the current origin if
     * none exists yet. Server-side (mutates persisted state); used by the breeding
     * hook so a player parent contributes a stable set of alleles.
     */
    public static Genotype getOrSeedGenotype(Player player, RandomSource random) {
        Genotype stored = getGenotype(player);
        if (!stored.isEmpty()) return stored;
        ResourceLocation rootId = ResourceLocation.tryParse(getRootId(player));
        if (rootId == null) rootId = RootRegistry.DEFAULT_ID;
        Genotype seeded = Heredity.seedGenotype(rootId, random);
        setGenotype(player, seeded);
        return seeded;
    }

    private static CompoundTag data(Player player) {
        //? if neoforge {
        return player.getData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA);
        //?} else {
        /*return persisted(player);
        *///?}
    }

    private static void store(Player player, CompoundTag tag) {
        //? if neoforge {
        player.setData(com.aetherianartificer.townstead.Townstead.PLAYER_ROOT_DATA, tag);
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
