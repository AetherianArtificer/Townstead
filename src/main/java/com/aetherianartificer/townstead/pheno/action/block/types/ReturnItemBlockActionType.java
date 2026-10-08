package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.resource.ResourceResolver;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Returns a declared item stack from a block transaction without spawning it in the world. */
public final class ReturnItemBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:return_item";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        ResourceResolver item = ResourceResolver.parse(json.get("item"));
        int count = GsonHelper.getAsInt(json, "count", 1);
        if (item == null || count < 1) return null;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) { return item.resolve(context) != null; }

            @Override public void run(BlockActionContext context) {
                var id = item.resolve(context);
                var resolved = id == null ? Items.AIR : BuiltInRegistries.ITEM.get(id);
                if (resolved == null || resolved == Items.AIR) { context.fail(); return; }
                context.returnItem(new ItemStack(resolved, count));
            }
        };
    }
}
