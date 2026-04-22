package com.aetherianartificer.townstead.compat.butchery;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Classifies Butchery's hammer-breakable head / skull blocks and maps each
 * category to its breakdown loot table and special armor drops.
 *
 * <p>The mod's {@code BreakheadProcedure} accepts blocks in
 * {@code butchery:heads} (excluding the unbreakable / eyeless / skullless
 * subsets), plus {@code skulls}, {@code ice_skull}, {@code wither_skull},
 * {@code iron_heads}, {@code spider_head}. Each category has its own
 * {@code butchery:blocks/X_breakdown} (or equivalent) loot table, and
 * three specific heads additionally drop vanilla-style armor helmets.
 *
 * <p>Trophy protection: by default the villager does NOT auto-hammer
 * blocks in {@link #TROPHY_BLACKLIST} or any block in the trophy tags
 * ({@code butchery:wither_skull}, {@code butchery:ice_skull}), because
 * those are rare / display-worthy drops the player likely wants to keep
 * whole. The {@code hammerTrophyHeads} config flag opts in to breaking
 * them.
 */
public final class HammerHelper {

    // ── Category tags ──

    public static final TagKey<Block> HEADS = blockTag("butchery:heads");
    public static final TagKey<Block> SKULLS = blockTag("butchery:skulls");
    public static final TagKey<Block> ICE_SKULL = blockTag("butchery:ice_skull");
    public static final TagKey<Block> WITHER_SKULL = blockTag("butchery:wither_skull");
    public static final TagKey<Block> IRON_HEADS = blockTag("butchery:iron_heads");
    public static final TagKey<Block> SPIDER_HEAD = blockTag("butchery:spider_head");
    public static final TagKey<Block> UNBREAKABLE_HEADS = blockTag("butchery:unbreakable_heads");
    public static final TagKey<Block> EYELESS_HEAD = blockTag("butchery:eyeless_head");
    public static final TagKey<Block> SKULLLESS_HEAD = blockTag("butchery:skullless_head");

    // ── Trophy-protection list ──

    /**
     * Blocks we leave alone unless {@code hammerTrophyHeads} is on. Armor-
     * drop heads (evoker / vindicator / pillager) are here because a
     * player who kills those mobs usually wants the armor specifically,
     * and may prefer to break the head themselves. Player / dragon heads
     * are obvious displays. Warden is a rare end-game trophy.
     */
    private static final Set<ResourceLocation> TROPHY_BLACKLIST = Set.of(
            id("butchery:evoker_head"),
            id("butchery:vindicator_head"),
            id("butchery:pillager_head"),
            id("butchery:warden_head"),
            id("minecraft:dragon_head"),
            id("minecraft:dragon_wall_head"),
            id("minecraft:player_head"),
            id("minecraft:player_wall_head")
    );

    /** Specific block IDs that carry an extra armor drop on top of head_breakdown. */
    public record ArmorDrop(ResourceLocation blockId, ResourceLocation armorItemId) {}

    public static final ArmorDrop[] ARMOR_DROPS = {
            new ArmorDrop(id("butchery:evoker_head"), id("butchery:evoker_robes_helmet")),
            new ArmorDrop(id("butchery:vindicator_head"), id("butchery:vindicatorarmor_helmet")),
            new ArmorDrop(id("butchery:pillager_head"), id("butchery:pillager_armor_helmet")),
    };

    private HammerHelper() {}

    // ── Category classification ──

    public enum Category {
        HEADS("butchery:blocks/head_breakdown"),
        SKULLS("butchery:blocks/skulls_breakdown"),
        EYELESS("butchery:blocks/eyeless_head_breakdown"),
        SKULLLESS("butchery:blocks/skullless_head_breakdown"),
        ICE_SKULL("butchery:blocks/ice_skull_breakdown"),
        WITHER_SKULL("butchery:blocks/wither_skull_breakdown"),
        IRON_HEADS("butchery:blocks/iron_heads_breakdown"),
        SPIDER("butchery:blocks/spider_eyes_drop");

        public final String lootPath;

        Category(String lootPath) {
            this.lootPath = lootPath;
        }
    }

    /**
     * Returns the category that drives loot + sounds for the given
     * block, or null if the block isn't hammer-breakable at all. This
     * does NOT apply trophy filtering; use {@link #shouldAutoHammer} for
     * that.
     */
    @Nullable
    public static Category classify(BlockState state) {
        if (state.is(UNBREAKABLE_HEADS)) return null;
        // Match the BreakheadProcedure branch order: specific sub-tags
        // first, then the umbrella heads tag. Order matters because
        // eyeless / skullless heads are ALSO in the heads tag and would
        // otherwise pick up the heads category drops.
        if (state.is(EYELESS_HEAD)) return Category.EYELESS;
        if (state.is(SKULLLESS_HEAD)) return Category.SKULLLESS;
        if (state.is(ICE_SKULL)) return Category.ICE_SKULL;
        if (state.is(WITHER_SKULL)) return Category.WITHER_SKULL;
        if (state.is(IRON_HEADS)) return Category.IRON_HEADS;
        if (state.is(SPIDER_HEAD)) return Category.SPIDER;
        if (state.is(SKULLS)) return Category.SKULLS;
        if (state.is(HEADS)) return Category.HEADS;
        return null;
    }

    /**
     * True if the villager should auto-hammer the given block. Filters
     * out trophies unless {@code hammerTrophyHeads} is on.
     */
    public static boolean shouldAutoHammer(BlockState state, boolean hammerTrophyHeads) {
        Category cat = classify(state);
        if (cat == null) return false;
        if (hammerTrophyHeads) return true;
        if (isTrophy(state)) return false;
        return true;
    }

    /** True if the block is one of the trophy-protected heads/skulls. */
    public static boolean isTrophy(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (TROPHY_BLACKLIST.contains(blockId)) return true;
        // Boss / rare skull categories are trophies as a whole.
        return state.is(WITHER_SKULL) || state.is(ICE_SKULL);
    }

    /**
     * Returns the extra armor helmet ID this block drops on top of
     * head_breakdown, or null if the block has no armor drop.
     */
    @Nullable
    public static ResourceLocation armorDropFor(BlockState state) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        for (ArmorDrop drop : ARMOR_DROPS) {
            if (drop.blockId.equals(blockId)) return drop.armorItemId;
        }
        return null;
    }

    /** Simple sanity check the item is actually registered in-world. */
    public static ItemStack resolveArmorStack(@Nullable ResourceLocation itemId) {
        if (itemId == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == net.minecraft.world.item.Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(item);
    }

    // ── Local helpers ──

    private static TagKey<Block> blockTag(String id) {
        //? if >=1.21 {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
        //?} else {
        /*int split = id.indexOf(':');
        return TagKey.create(Registries.BLOCK, new ResourceLocation(id.substring(0, split), id.substring(split + 1)));
        *///?}
    }

    private static ResourceLocation id(String s) {
        //? if >=1.21 {
        return ResourceLocation.parse(s);
        //?} else {
        /*int split = s.indexOf(':');
        return new ResourceLocation(s.substring(0, split), s.substring(split + 1));
        *///?}
    }
}
