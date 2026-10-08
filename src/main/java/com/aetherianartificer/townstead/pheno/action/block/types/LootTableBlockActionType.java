package com.aetherianartificer.townstead.pheno.action.block.types;

import com.aetherianartificer.townstead.pheno.action.block.BlockAction;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionContext;
import com.aetherianartificer.townstead.pheno.action.block.BlockActionType;
import com.aetherianartificer.townstead.pheno.resource.ResourceResolver;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootParams;
//? if >=1.21 {
import net.minecraft.world.level.storage.loot.LootTable;
//?}
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/** Rolls a loot table and returns its stacks to the surrounding block transaction. */
public final class LootTableBlockActionType implements BlockActionType {
    public static final String KEY = "pheno:loot_table";

    @Override public String key() { return KEY; }

    @Override public BlockAction parse(JsonObject json) {
        ResourceResolver table = ResourceResolver.parse(json.get("table"));
        if (table == null) return null;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext context) { return table.resolve(context) != null; }

            @Override public void run(BlockActionContext context) {
                var id = table.resolve(context);
                var server = context.level().getServer();
                if (id == null || server == null) { context.fail(); return; }
                //? if >=1.21 {
                ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE, id);
                LootTable loot = server.reloadableRegistries().getLootTable(key);
                if (loot == null) return;
                loot.getRandomItems(new LootParams.Builder(context.level())
                        .create(LootContextParamSets.EMPTY)).forEach(context::returnItem);
                //?} else {
                /*net.minecraft.world.level.storage.loot.LootTable loot = server.getLootData().getLootTable(id);
                if (loot == null || loot == net.minecraft.world.level.storage.loot.LootTable.EMPTY) return;
                loot.getRandomItems(new LootParams.Builder(context.level())
                        .create(LootContextParamSets.EMPTY)).forEach(context::returnItem);
                *///?}
            }
        };
    }
}
