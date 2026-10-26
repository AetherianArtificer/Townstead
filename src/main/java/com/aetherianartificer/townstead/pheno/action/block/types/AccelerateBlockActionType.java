package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Runs the target block entity's own ticker {@code ticks} extra times, so a furnace, brewing
 * stand, pot or any modded station works that much further along. Fuel per item is unchanged; a
 * station that times itself by the world clock does not speed up. Blocks in
 * {@code #townstead:never_accelerate} (spawners and the like) are never touched. Compose it with
 * {@code pheno:at} and a region selector to speed up stations around the bearer.
 *
 * <p>JSON: {@code { "type":"pheno:accelerate", "ticks":5 }}</p>
 */
public final class AccelerateBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:accelerate";
    public static final int MAX_TICKS = 200;
    public static final TagKey<Block> NEVER = TagKey.create(Registries.BLOCK, id("townstead", "never_accelerate"));

    @Override
    public String key() {
        return KEY;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public BlockAction parse(JsonObject json) {
        int ticks = Math.max(1, Math.min(MAX_TICKS, GsonHelper.getAsInt(json, "ticks", 1)));
        return ctx -> {
            ServerLevel level = ctx.level();
            if (!level.shouldTickBlocksAt(ctx.pos())) return;
            BlockState state = level.getBlockState(ctx.pos());
            if (!state.hasBlockEntity() || state.is(NEVER)) return;
            BlockEntity blockEntity = level.getBlockEntity(ctx.pos());
            if (blockEntity == null) return;
            BlockEntityTicker ticker = state.getTicker(level, blockEntity.getType());
            if (ticker == null) return;
            for (int i = 0; i < ticks && !blockEntity.isRemoved(); i++) {
                ticker.tick(level, ctx.pos(), state, blockEntity);
            }
        };
    }

    private static ResourceLocation id(String namespace, String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
