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
 * Sets the target block to {@code block} (Apoli's block {@code set_block}).
 *
 * <p>JSON: {@code { "type":"pheno:set_block", "block":"minecraft:cobblestone" }}</p>
 */
public final class SetBlockBlockActionType implements BlockActionType {

    public static final String KEY = "pheno:set_block";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockAction parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "block", ""));
        if (id == null) return null;
        return ctx -> {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block != null) ctx.level().setBlock(ctx.pos(), block.defaultBlockState(), 3);
        };
    }
}
