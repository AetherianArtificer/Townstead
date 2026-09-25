package com.aetherianartificer.townstead.client.haze;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.block.haze.HazeBlock;
import com.aetherianartificer.townstead.block.haze.HazeKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
//? if neoforge {
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;
//?} else if forge {
/*import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.data.ModelData;
*///?}
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Every haze state's model. A cell's look comes from its kind, which arrives from the server,
 * so the quads are built on demand here rather than baked from JSON: a flat splat for
 * {@code layer}, two crossed planes for {@code cross}, a cube for {@code solid}, and and nothing for a
 * {@code cloud}, which the block draws in particles. A kind without its own texture gets the grayscale default,
 * tinted to the kind's colour.
 */
public final class HazeModel implements BakedModel {

    private static final Map<BlockState, List<BakedQuad>> QUADS = new ConcurrentHashMap<>();
    private static final float LAYER_Y = 1f / 32f;

    /** Kinds or sprites changed; every cached mesh is stale. */
    public static void invalidate() {
        QUADS.clear();
    }

    @Nullable
    private static HazeKind kind(@Nullable BlockState state) {
        return state == null ? null : HazeBlock.kindOf(state, true);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        if (side != null || state == null) return List.of();
        HazeKind kind = kind(state);
        if (kind == null || kind.shape() == HazeKind.Shape.CLOUD) return List.of();
        return QUADS.computeIfAbsent(state, s -> build(kind));
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
                                   ModelData data, @Nullable RenderType layer) {
        if (layer != null && state != null && !getRenderTypes(state, random, data).contains(layer)) {
            return List.of();
        }
        return getQuads(state, side, random);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        HazeKind kind = kind(state);
        if (kind == null || kind.shape() == HazeKind.Shape.CLOUD) return ChunkRenderTypeSet.none();
        return ChunkRenderTypeSet.of(switch (kind.shape()) {
            case LAYER -> RenderType.translucent();
            case SOLID -> RenderType.solid();
            default -> RenderType.cutout();
        });
    }

    private static List<BakedQuad> build(HazeKind kind) {
        boolean custom = kind.texture() != null;
        TextureAtlasSprite sprite = sprite(custom ? kind.texture() : switch (kind.shape()) {
            case LAYER -> id("haze/layer");
            // A solid cube renders in the solid layer, where a transparent default would turn black.
            case SOLID -> ResourceLocation.tryParse("minecraft:block/stone");
            default -> id("haze/cross");
        });
        int tint = custom ? -1 : 0;
        List<BakedQuad> quads = new ArrayList<>();
        if (kind.shape() == HazeKind.Shape.LAYER) {
            doubleSided(quads, sprite, tint, Direction.UP, true,
                    0, LAYER_Y, 0, 0, LAYER_Y, 1, 1, LAYER_Y, 1, 1, LAYER_Y, 0);
        } else if (kind.shape() == HazeKind.Shape.SOLID) {
            doubleSided(quads, sprite, tint, Direction.UP, true, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0);
            doubleSided(quads, sprite, tint, Direction.DOWN, true, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1);
            doubleSided(quads, sprite, tint, Direction.NORTH, true, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0);
            doubleSided(quads, sprite, tint, Direction.SOUTH, true, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1);
            doubleSided(quads, sprite, tint, Direction.WEST, true, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1);
            doubleSided(quads, sprite, tint, Direction.EAST, true, 1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0);
        } else {
            doubleSided(quads, sprite, tint, Direction.NORTH, false,
                    0, 1, 0, 0, 0, 0, 1, 0, 1, 1, 1, 1);
            doubleSided(quads, sprite, tint, Direction.EAST, false,
                    0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0);
        }
        return List.copyOf(quads);
    }

    /** One quad through the four corners given, and the same quad wound the other way. */
    private static void doubleSided(List<BakedQuad> out, TextureAtlasSprite sprite, int tint, Direction face,
                                    boolean shade, float... c) {
        float[][] uv = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
        int[] front = new int[32];
        int[] back = new int[32];
        for (int i = 0; i < 4; i++) {
            vertex(front, i, sprite, c[i * 3], c[i * 3 + 1], c[i * 3 + 2], uv[i][0], uv[i][1], face);
            int j = 3 - i;
            vertex(back, i, sprite, c[j * 3], c[j * 3 + 1], c[j * 3 + 2], uv[j][0], uv[j][1], face.getOpposite());
        }
        out.add(quad(front, tint, face, sprite, shade));
        out.add(quad(back, tint, face.getOpposite(), sprite, shade));
    }

    /** One vertex in the block format: position, colour, uv, light, normal. */
    private static void vertex(int[] data, int index, TextureAtlasSprite sprite, float x, float y, float z,
                               float u, float v, Direction normal) {
        int base = index * 8;
        data[base] = Float.floatToRawIntBits(x);
        data[base + 1] = Float.floatToRawIntBits(y);
        data[base + 2] = Float.floatToRawIntBits(z);
        data[base + 3] = 0xFFFFFFFF;
        data[base + 4] = Float.floatToRawIntBits(sprite.getU0() + (sprite.getU1() - sprite.getU0()) * u);
        data[base + 5] = Float.floatToRawIntBits(sprite.getV0() + (sprite.getV1() - sprite.getV0()) * v);
        data[base + 6] = 0;
        data[base + 7] = (normal.getStepX() * 127 & 0xFF)
                | ((normal.getStepY() * 127 & 0xFF) << 8)
                | ((normal.getStepZ() * 127 & 0xFF) << 16);
    }

    private static BakedQuad quad(int[] data, int tint, Direction face, TextureAtlasSprite sprite, boolean shade) {
        //? if >=1.21 {
        return new BakedQuad(data, tint, face, sprite, shade, false);
        //?} else {
        /*return new BakedQuad(data, tint, face, sprite, shade);
        *///?}
    }

    private static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(id);
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(Townstead.MOD_ID, path);
        *///?}
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return false;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return sprite(id("haze/layer"));
    }

    @Override
    public ItemTransforms getTransforms() {
        return ItemTransforms.NO_TRANSFORMS;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
