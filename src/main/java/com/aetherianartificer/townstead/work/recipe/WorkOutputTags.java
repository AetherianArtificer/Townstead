package com.aetherianartificer.townstead.work.recipe;

import com.aetherianartificer.townstead.Townstead;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What each trade is allowed to be asked to make, as an item tag per trade.
 *
 * <p>A station role cannot answer this. A furnace bakes a potato and smelts an iron ingot at the
 * same role, from the same recipe type, so neither the workstation def nor the recipe tells you
 * which trade a result belongs to. Nor does "is it edible": a butcher's day produces raw sausage,
 * mince, hide and intestines, none of which anyone eats as they are.</p>
 *
 * <p>So the set is stated rather than inferred. A recipe is offered to a trade only when its output
 * carries that trade's tag — no default, no heuristic, nothing for Townstead to keep noticing as
 * mods are added. Tags merge across packs, so a food mod, this mod and a player's own pack can each
 * contribute, and the shipped tags lean on the community food tags rather than listing the world:</p>
 *
 * <pre>
 * data/townstead/tags/item/cook_output.json
 *   { "replace": false, "values": [ {"id": "#c:foods", "required": false} ] }
 * </pre>
 *
 * <p>The cost is honest and worth stating: <strong>an output nobody has tagged is invisible</strong>.
 * That is the deliberate trade — a missing line in a tag file is findable and fixable by anyone,
 * where a missing case in a heuristic is not.</p>
 */
public final class WorkOutputTags {

    private WorkOutputTags() {}

    /** Things a cook may be ordered to make. */
    public static final TagKey<Item> COOK = tag("cook_output");
    /** Things a barista may be ordered to make. */
    public static final TagKey<Item> BREW = tag("brew_output");
    /** Things a butcher may be ordered to make. Not food: sausage, mince, hide, intestines. */
    public static final TagKey<Item> BUTCHER = tag("butcher_output");

    private static TagKey<Item> tag(String path) {
        //? if >=1.21 {
        return TagKey.create(Registries.ITEM,
                ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, path));
        //?} else {
        /*return TagKey.create(Registries.ITEM, new ResourceLocation(Townstead.MOD_ID, path));
        *///?}
    }

    /** Whether this output is one the trade is allowed to be asked for. */
    public static boolean allows(TagKey<Item> tag, ResourceLocation output) {
        if (tag == null || output == null) return false;
        if (!BuiltInRegistries.ITEM.containsKey(output)) return false;
        return new ItemStack(BuiltInRegistries.ITEM.get(output)).is(tag);
    }
}
