package com.aetherianartificer.townstead.pheno.action.types;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonObject;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tells the bearer which blocks a selector finds: each kind of block with its count and the
 * distance to the nearest one, above the hotbar. Nothing happens when the bearer is not a player.
 * The action fails when no block is found, so an ability built on it is not charged for nothing.
 *
 * <p>JSON: {@code { "type":"pheno:report_blocks",
 * "blocks":{ "type":"pheno:column", "direction":"down", "distance":64,
 * "where":{ "type":"pheno:in_tag", "tag":"c:ores" } } }}</p>
 */
public final class ReportBlocksActionType implements ActionType {

    public static final String KEY = "pheno:report_blocks";
    private static final int MAX_KINDS = 6;

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        BlockSelector selector = json.has("blocks") ? BlockSelectors.parse(json.get("blocks")) : null;
        if (selector == null) return null;
        return ctx -> {
            List<BlockPos> found = selector.select(SelectorContext.of(ctx));
            if (found.isEmpty()) {
                ctx.fail();
                return;
            }
            if (!(ctx.origin() instanceof ServerPlayer viewer)) return;
            Map<Block, int[]> kinds = new LinkedHashMap<>();
            BlockPos here = viewer.blockPosition();
            for (BlockPos pos : found) {
                Block block = viewer.level().getBlockState(pos).getBlock();
                int distance = (int) Math.round(Math.sqrt(pos.distSqr(here)));
                int[] entry = kinds.computeIfAbsent(block, b -> new int[]{0, Integer.MAX_VALUE});
                entry[0]++;
                entry[1] = Math.min(entry[1], distance);
            }
            MutableComponent line = Component.empty();
            int shown = 0;
            for (Map.Entry<Block, int[]> kind : kinds.entrySet()) {
                if (shown == MAX_KINDS) {
                    line.append(Component.literal("  +" + (kinds.size() - MAX_KINDS)).withStyle(ChatFormatting.GRAY));
                    break;
                }
                if (shown++ > 0) line.append(Component.literal("   "));
                line.append(kind.getKey().getName())
                        .append(Component.literal(" x" + kind.getValue()[0]).withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal(" (" + kind.getValue()[1] + ")").withStyle(ChatFormatting.GRAY));
            }
            viewer.displayClientMessage(line, true);
        };
    }
}
