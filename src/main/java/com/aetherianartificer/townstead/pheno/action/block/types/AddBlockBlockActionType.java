package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;

/**
 * Places {@code block} at the target only if the current block is replaceable (air,
 * grass, water, ...), so it adds rather than overwrites (Apoli's block {@code add_block}).
 *
 * <p>JSON: {@code { "type":"pheno:add_block", "block":"minecraft:snow" }}</p>
 */
public final class AddBlockBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:add_block";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "block", ""));
        if (id == null) return null;
        return ctx -> {
            if (!ctx.level().getBlockState(ctx.pos()).canBeReplaced()) return;
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null) ctx.level().setBlock(ctx.pos(), block.defaultBlockState(), 3);
        };
    }
}
