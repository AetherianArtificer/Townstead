package com.aetherianartificer.townstead.pheno.action.block;

import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
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
            return new BlockAction() {
                @Override public boolean canRun(BlockActionContext ctx) {
                    return actions.stream().allMatch(action -> action.canRun(ctx));
                }

                @Override public void run(BlockActionContext ctx) {
                    actions.forEach(action -> action.run(ctx));
                }
            };
        }
        if (!element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        BlockAction inner = BlockActionTypes.get(GsonHelper.getAsString(json, "type", ""))
                .map(t -> t.parse(json)).orElse(null);
        if (inner == null) return null;
        // Common action guard. Because meta actions parse their child through this same method,
        // a condition on a use_block nested inside offset is evaluated at the offset block.
        if (json.has("condition")) {
            BlockCondition condition = BlockConditions.parse(json.get("condition"));
            if (condition == null) return null;
            BlockAction core = inner;
            inner = new BlockAction() {
                @Override public boolean canRun(BlockActionContext ctx) {
                    return condition.test(ctx.level(), ctx.pos()) && core.canRun(ctx);
                }

                @Override public void run(BlockActionContext ctx) {
                    if (canRun(ctx)) core.run(ctx);
                    else ctx.fail();
                }
            };
        }
        if (!json.has("on")) return inner;
        BlockSelector selector = BlockSelectors.parse(json.get("on"));
        if (selector == null) return null;
        BlockAction core = inner;
        return new BlockAction() {
            @Override public boolean canRun(BlockActionContext ctx) {
                SelectorContext sctx = SelectorContext.ofBlock(ctx.level(), ctx.pos(), ctx.cause());
                for (BlockPos pos : selector.select(sctx)) {
                    if (core.canRun(ctx.at(pos))) return true;
                }
                return false;
            }

            @Override public void run(BlockActionContext ctx) {
                SelectorContext sctx = SelectorContext.ofBlock(ctx.level(), ctx.pos(), ctx.cause());
                boolean ran = false;
                for (BlockPos pos : selector.select(sctx)) {
                    BlockActionContext selected = ctx.at(pos);
                    if (!core.canRun(selected)) continue;
                    core.run(selected);
                    ran = true;
                }
                if (!ran) ctx.fail();
            }
        };
    }
}
