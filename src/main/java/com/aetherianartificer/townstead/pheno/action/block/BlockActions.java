package com.aetherianartificer.townstead.pheno.action.block;

import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a block-action JSON element into a {@link BlockAction}. An array runs every action in
 * order; an object dispatches by {@code "type"}, then, if it carries an {@code on}, runs once per
 * selected block position (the block analogue of an entity action's {@code on}). Clone of
 * {@code Actions}.
 */
public final class BlockActions {

    private BlockActions() {}

    @Nullable
    public static BlockAction parse(@Nullable JsonElement element) {
        if (element == null) return null;
        if (element.isJsonArray()) {
            List<BlockAction> actions = new ArrayList<>();
            for (JsonElement child : element.getAsJsonArray()) {
                BlockAction action = parse(child);
                if (action == null) return null;
                actions.add(action);
            }
            if (actions.isEmpty()) return null;
            return ctx -> actions.forEach(a -> a.run(ctx));
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        BlockAction inner = BlockActionTypes.get(GsonHelper.getAsString(json, "type", ""))
                .map(t -> t.parse(json)).orElse(null);
        if (inner == null) return null;
        if (!json.has("on")) return inner;
        BlockSelector selector = BlockSelectors.parse(json.get("on"));
        if (selector == null) return null;
        BlockAction core = inner;
        return ctx -> {
            SelectorContext sctx = SelectorContext.ofBlock(ctx.level(), ctx.pos(), ctx.cause());
            for (BlockPos pos : selector.select(sctx)) {
                core.run(ctx.at(pos));
            }
        };
    }
}
