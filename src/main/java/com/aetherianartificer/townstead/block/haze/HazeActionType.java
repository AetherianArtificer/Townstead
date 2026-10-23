package com.aetherianartificer.townstead.block.haze;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.ActionType;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Raises haze of one kind at the selected blocks. Only air and existing haze are filled, so a
 * cloud never replaces a torch or a flower; grounded kinds skip cells with nothing solid below.
 *
 * <p>JSON: {@code { "type":"pheno:haze", "kind":"townstead:flour",
 * "blocks":{ "radius":3, "where":{ "type":"air" } } }}</p>
 */
public final class HazeActionType implements ActionType {

    public static final String KEY = "pheno:haze";

    @Override
    public String key() {
        return KEY;
    }

    @Override
    public Action parse(JsonObject json) {
        ResourceLocation kindId = ResourceLocation.tryParse(GsonHelper.getAsString(json, "kind", ""));
        BlockSelector selector = json.has("blocks") ? BlockSelectors.parse(json.get("blocks")) : null;
        if (kindId == null || selector == null) return null;
        int density = Math.max(1, Math.min(HazeBlock.MAX_DENSITY,
                GsonHelper.getAsInt(json, "density", HazeBlock.MAX_DENSITY)));
        return ctx -> {
            if (!(ctx.level() instanceof ServerLevel level)) return;
            int index = HazeKinds.server().indexOf(kindId);
            if (index < 0) {
                ctx.fail();
                return;
            }
            BlockState haze = Townstead.HAZE.get().defaultBlockState()
                    .setValue(HazeBlock.KIND, index).setValue(HazeBlock.DENSITY, density);
            HazeKind kind = HazeKinds.server().byIndex(index);
            boolean grounded = kind != null && kind.shape().grounded();
            boolean placed = false;
            for (BlockPos selected : selector.select(SelectorContext.of(ctx))) {
                BlockPos pos = grounded ? groundCell(level, selected) : selected;
                if (pos == null) continue;
                if (!haze.canSurvive(level, pos)) continue;
                level.setBlock(pos, haze, Block.UPDATE_ALL);
                placed = true;
            }
            if (!placed) ctx.fail();
        };
    }

    private static boolean open(BlockState state) {
        return state.isAir() || state.getBlock() instanceof HazeBlock;
    }

    /**
     * Where a grounded spill lands for a selected cell, or null. A creature standing in grass,
     * or on a path, farmland or slab, occupies a cell that is not air: short replaceable cover
     * such as grass is overwritten (a flower is not replaceable, so it stays), and a partial
     * block pushes the spill up onto its top.
     */
    private static BlockPos groundCell(ServerLevel level, BlockPos pos) {
        BlockState current = level.getBlockState(pos);
        if (open(current)) return pos;
        if (current.canBeReplaced() && current.getFluidState().isEmpty() && !current.hasBlockEntity()) {
            return pos;
        }
        var shape = current.getCollisionShape(level, pos);
        if (!shape.isEmpty() && shape.max(net.minecraft.core.Direction.Axis.Y) < 1.0) {
            BlockPos up = pos.above();
            return open(level.getBlockState(up)) ? up : null;
        }
        return null;
    }
}
