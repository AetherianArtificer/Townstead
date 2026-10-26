package com.aetherianartificer.townstead.compat.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Rope handling shared by the Let's Do climbing crops (Farm &amp; Charm tomatoes, Brewery hops).
 * A segment climbs only when its {@code supported} flag is set and a hanging rope block is
 * directly above it. The farmer sets the flag; the player hangs the rope.
 */
public final class ClimbingCropRope {
    private static final String ROPE_NAMESPACE = "farm_and_charm";
    private static final String ROPE_PATH = "rope";

    private ClimbingCropRope() {}

    /** True if rope applied here lets the plant grow: flag unset, and a rope block waits above. */
    public static boolean needsRope(ServerLevel level, BlockPos pos, BlockState state) {
        BooleanProperty supported = supportedProperty(state);
        if (supported == null || state.getValue(supported)) return false;
        return isRopeId(level.getBlockState(pos.above()).getBlock().builtInRegistryHolder().key().location());
    }

    public static boolean isRope(ItemStack stack) {
        return !stack.isEmpty() && isRopeId(stack.getItem().builtInRegistryHolder().key().location());
    }

    public static boolean applyRope(ServerLevel level, BlockPos pos, BlockState state) {
        BooleanProperty supported = supportedProperty(state);
        if (supported == null || state.getValue(supported)) return false;
        return level.setBlock(pos, state.setValue(supported, true), Block.UPDATE_CLIENTS);
    }

    private static BooleanProperty supportedProperty(BlockState state) {
        return state.getBlock().getStateDefinition().getProperty("supported") instanceof BooleanProperty property
                ? property : null;
    }

    private static boolean isRopeId(ResourceLocation id) {
        return ROPE_NAMESPACE.equals(id.getNamespace()) && ROPE_PATH.equals(id.getPath());
    }
}
