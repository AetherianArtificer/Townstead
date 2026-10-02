package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.ai.Chore;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;

import java.util.function.Predicate;

/**
 * Chore tool matching across MCA lines. MCA 7.7.37 replaced {@code Chore#getToolType()}
 * with a tag-aware {@code Chore#matchesTool(ItemStack)}, which also matches modded tools
 * carrying the vanilla tool tags. That rewrite dropped PROSPECT's pickaxe binding, so
 * prospecting is matched here instead and stays stocked on both lines.
 */
public final class McaChoreTools {
    private McaChoreTools() {}

    /** Null when the chore needs no tool. */
    public static Predicate<ItemStack> toolMatcher(Chore chore) {
        if (chore == null || chore == Chore.NONE) return null;
        if (chore == Chore.PROSPECT) return McaChoreTools::isPickaxe;
        //? if >=1.21 {
        return chore::matchesTool;
        //?} else {
        /*Class<?> toolType = chore.getToolType();
        return toolType == null ? null : stack -> toolType.isInstance(stack.getItem());
        *///?}
    }

    private static boolean isPickaxe(ItemStack stack) {
        return stack.getItem() instanceof PickaxeItem || stack.is(ItemTags.PICKAXES);
    }
}
