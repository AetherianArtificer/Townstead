package com.aetherianartificer.townstead.origin.gene.types;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.origin.gene.GeneDisplay;
import com.aetherianartificer.townstead.origin.gene.GeneInstance;
import com.aetherianartificer.townstead.origin.gene.GeneType;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Lets the bearer harvest (get drops from) blocks matching a filter even without the
 * right tool (Origins' {@code modify_harvest}, e.g. Shulker strong arms mining stone).
 * The filter is a block {@code tag} or {@code block} id; {@code allow} (default true)
 * grants harvest. Matched on the target block's state, so it works on both loaders.
 *
 * <p>JSON: {@code { "type":"pheno:modify_harvest", "tag":"minecraft:base_stone_overworld" }}</p>
 */
public final class ModifyHarvestGeneType implements GeneType {

    public static final String KEY = "pheno:modify_harvest";

    public record Instance(@Nullable TagKey<Block> tag, @Nullable ResourceLocation blockId, boolean allow)
            implements GeneInstance {
        @Override public String typeKey() { return KEY; }
        @Override public GeneDisplay display() { return GeneDisplay.PRESENCE; }

        public boolean matches(BlockState state) {
            if (tag != null && state.is(tag)) return true;
            return blockId != null && state.is(BuiltInRegistries.BLOCK.get(blockId));
        }
    }

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public GeneInstance parse(JsonObject json) {
        // Accept either direct tag/block, or an Apoli-style block_condition wrapper.
        JsonObject filter = json.has("block_condition") && json.get("block_condition").isJsonObject()
                ? json.getAsJsonObject("block_condition") : json;
        TagKey<Block> tag = null;
        ResourceLocation blockId = null;
        if (filter.has("tag")) {
            ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(filter, "tag", ""));
            if (id != null) tag = TagKey.create(Registries.BLOCK, id);
        } else if (filter.has("block")) {
            blockId = DataPackLang.parseId(GsonHelper.getAsString(filter, "block", ""));
        }
        if (tag == null && blockId == null) return null;
        boolean allow = GsonHelper.getAsBoolean(json, "allow", true);
        return new Instance(tag, blockId, allow);
    }
}
