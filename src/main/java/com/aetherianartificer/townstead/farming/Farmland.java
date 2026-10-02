package com.aetherianartificer.townstead.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
//? if neoforge {
import net.neoforged.neoforge.common.ItemAbilities;
//?} else if forge {
/*import net.minecraftforge.common.ToolActions;
*///?}
import org.jetbrains.annotations.Nullable;

/**
 * Farmland beyond vanilla's {@link FarmBlock}. Mods whose farmland is its own block (TerraFirmaCraft)
 * join through the {@code townstead:farmland} block tag, and their dirt tills through the block's own
 * hoe action, so the farmer gets exactly what a player's hoe would make.
 */
public final class Farmland {
    public static final TagKey<Block> TAG = TagKey.create(Registries.BLOCK,
            ResourceLocation.tryParse("townstead:farmland"));

    private Farmland() {}

    public static boolean is(BlockState state) {
        return state.getBlock() instanceof FarmBlock || state.is(TAG);
    }

    /**
     * Watered farmland. Vanilla reads its moisture; farmland without that property counts as
     * watered with water in vanilla's reach, which is also the source-water rule TFC applies.
     */
    public static boolean isMoist(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.hasProperty(FarmBlock.MOISTURE)) return state.getValue(FarmBlock.MOISTURE) > 0;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-4, 0, -4), pos.offset(4, 1, 4))) {
            if (level.getFluidState(p).is(FluidTags.WATER)) return true;
        }
        return false;
    }

    public static boolean isHoe(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof HoeItem) return true;
        //? if neoforge {
        return stack.canPerformAction(ItemAbilities.HOE_TILL);
        //?} else if forge {
        /*return stack.canPerformAction(ToolActions.HOE_TILL);
        *///?}
    }

    /** Vanilla ground the farmer has always tilled straight to vanilla farmland. */
    public static boolean isVanillaTillable(BlockState state) {
        return state.is(Blocks.DIRT) || state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT_PATH) || state.is(Blocks.COARSE_DIRT);
    }

    /**
     * Whether a hoe could make farmland here once the block above is cleared. Modded dirt is asked
     * directly when nothing stands on it; under a weed it counts until the till itself asks.
     */
    public static boolean canTill(Level level, BlockPos pos, BlockState state) {
        if (is(state)) return false;
        if (isVanillaTillable(state)) return true;
        if (!state.is(BlockTags.DIRT)) return false;
        if ("minecraft".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace())) return false;
        return !level.getBlockState(pos.above()).isAir() || tilled(level, pos, state, null) != null;
    }

    /**
     * The farmland a hoe makes of this block, or null when it makes something else or nothing.
     * Blocks that check for air above (vanilla, TFC) answer null while anything stands on them.
     */
    @Nullable
    public static BlockState tilled(Level level, BlockPos pos, BlockState state, @Nullable ItemStack hoe) {
        ItemStack tool = hoe != null && !hoe.isEmpty() ? hoe : new ItemStack(Items.IRON_HOE);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
        UseOnContext context = new UseOnContext(level, null, InteractionHand.MAIN_HAND, tool, hit) {};
        //? if neoforge {
        BlockState result = state.getToolModifiedState(context, ItemAbilities.HOE_TILL, true);
        //?} else if forge {
        /*BlockState result = state.getToolModifiedState(context, ToolActions.HOE_TILL, true);
        *///?}
        return result != null && is(result) ? result : null;
    }
}
