package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Farmland that stores nutrients crops use up (TerraFirmaCraft). The farmer tops them up with the
 * mod's own fertilizers on cells painted {@code FERTILIZED_NUTRIENTS}.
 */
public interface FarmerNutrientCompat {
    String modId();

    /** True when some nutrient here has room for more. */
    boolean wantsFeeding(ServerLevel level, BlockPos soilPos);

    boolean isFertilizer(ItemStack stack);

    /** True when one of this stack would mostly land, rather than spill over a full nutrient. */
    boolean wouldHelp(ServerLevel level, BlockPos soilPos, ItemStack stack);

    /** Applies one item of the stack. The caller shrinks the stack on success. */
    boolean feed(ServerLevel level, BlockPos soilPos, ItemStack stack);

    /** A fertilizer to show in the Field Post palette, or null when none is known. */
    @Nullable Item icon();
}
