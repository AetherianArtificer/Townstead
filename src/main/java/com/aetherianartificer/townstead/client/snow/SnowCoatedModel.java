package com.aetherianartificer.townstead.client.snow;

import com.aetherianartificer.townstead.block.SnowCoatedBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
//?} else if forge {
/*import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
*///?}
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/** Delegates the original model's behavior, adding only the shaped, cosmetic snow quads. */
public final class SnowCoatedModel extends BakedModelWrapper<BakedModel> {
    private final List<BakedQuad> snow;

    public SnowCoatedModel(BakedModel original, List<BakedQuad> snow) {
        super(original);
        this.snow = List.copyOf(snow);
    }

    private static boolean coated(@Nullable BlockState state) {
        return state != null && state.hasProperty(SnowCoatedBlock.SNOW_COATED)
                && state.getValue(SnowCoatedBlock.SNOW_COATED);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return append(originalModel.getQuads(state, side, random), coated(state) && side == null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData data, @Nullable RenderType layer) {
        List<BakedQuad> base = layer == null || state == null
                || originalModel.getRenderTypes(state, random, data).contains(layer)
                ? originalModel.getQuads(state, side, random, data, layer) : List.of();
        return append(base, coated(state) && side == null
                && (layer == null || layer == RenderType.cutoutMipped()));
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        var base = originalModel.getRenderTypes(state, random, data);
        return coated(state) ? ChunkRenderTypeSet.union(base, ChunkRenderTypeSet.of(RenderType.cutoutMipped())) : base;
    }

    private List<BakedQuad> append(List<BakedQuad> base, boolean includeSnow) {
        if (!includeSnow) return base;
        List<BakedQuad> result = new ArrayList<>(base.size() + snow.size());
        result.addAll(base);
        result.addAll(snow);
        return result;
    }
}
