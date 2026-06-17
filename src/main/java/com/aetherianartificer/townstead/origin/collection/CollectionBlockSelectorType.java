package com.aetherianartificer.townstead.origin.collection;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectorType;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects the stored positions of an {@code of: block} collection on the focus (the block analogue
 * of {@code CollectionSelectorType}, the same {@code pheno:collection} id resolved in a block
 * selector slot). Entries that no longer decode are skipped. This is how a stored block set feeds a
 * block action's {@code on}.
 */
public final class CollectionBlockSelectorType implements BlockSelectorType {

    public static final String KEY = "pheno:collection";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public BlockSelector parse(JsonObject json) {
        ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "collection", ""));
        if (id == null) return null;
        return ctx -> {
            if (ctx.self() == null) return List.of();
            List<BlockPos> out = new ArrayList<>();
            for (String element : CollectionValues.elements(ctx.self(), id)) {
                BlockPos pos = CollectionElement.toBlock(element);
                if (pos != null) out.add(pos);
            }
            return out;
        };
    }
}
