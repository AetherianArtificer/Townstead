package com.aetherianartificer.townstead.culture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The wall around a culture village, as one structure piece of the village: it saves with the world
 * like any other piece and builds the part of the wall each chunk holds. The wall follows the ground
 * column by column, stands on a short footing where the ground dips, leaves its gates open or closes
 * them with gate blocks, and can set a template at each corner, such as a watchtower. A village
 * palette dresses the wall like the rest of the village.
 */
public final class VillageEnclosurePiece extends StructurePiece {
    private static final int FOOTING = VillageFoundations.DEPTH;
    private final int[] wall;
    private final int[] gates;
    private final int[] corners;
    private final List<String> layers;
    private final @Nullable String gate;
    private final @Nullable ResourceLocation corner;
    private final int cornerGround;

    public VillageEnclosurePiece(EnclosurePlan.Plan plan, VillageLayouts.Enclosure enclosure, int minY, int maxY) {
        super(VillagePieceTypes.ENCLOSURE.get(), 0, bounds(plan, minY, maxY));
        this.wall = flatten(plan.wall());
        List<long[]> gateCells = new ArrayList<>();
        List<Boolean> alongZ = new ArrayList<>();
        for (EnclosurePlan.Gate g : plan.gates()) {
            for (long[] cell : g.cells()) { gateCells.add(cell); alongZ.add(g.alongZ()); }
        }
        this.gates = new int[gateCells.size() * 3];
        for (int i = 0; i < gateCells.size(); i++) {
            gates[i * 3] = (int) gateCells.get(i)[0];
            gates[i * 3 + 1] = (int) gateCells.get(i)[1];
            gates[i * 3 + 2] = alongZ.get(i) ? 1 : 0;
        }
        this.corners = enclosure.corner() == null ? new int[0] : flatten(awayFromGates(plan.corners(), gateCells));
        this.layers = enclosure.wall();
        this.gate = enclosure.gate();
        this.corner = enclosure.corner();
        this.cornerGround = enclosure.cornerGround();
    }

    public VillageEnclosurePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(VillagePieceTypes.ENCLOSURE.get(), tag);
        this.wall = tag.getIntArray("Wall");
        this.gates = tag.getIntArray("Gates");
        this.corners = tag.getIntArray("Corners");
        List<String> read = new ArrayList<>();
        for (Tag layer : tag.getList("Layers", Tag.TAG_STRING)) read.add(layer.getAsString());
        this.layers = List.copyOf(read);
        this.gate = tag.contains("Gate") ? tag.getString("Gate") : null;
        this.corner = tag.contains("Corner") ? ResourceLocation.tryParse(tag.getString("Corner")) : null;
        this.cornerGround = tag.getInt("CornerGround");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putIntArray("Wall", wall);
        tag.putIntArray("Gates", gates);
        tag.putIntArray("Corners", corners);
        ListTag list = new ListTag();
        for (String layer : layers) list.add(StringTag.valueOf(layer));
        tag.put("Layers", list);
        if (gate != null) tag.putString("Gate", gate);
        if (corner != null) tag.putString("Corner", corner.toString());
        tag.putInt("CornerGround", cornerGround);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                            RandomSource random, BoundingBox chunk, ChunkPos chunkPos, BlockPos pivot) {
        List<BlockState> states = new ArrayList<>();
        for (String id : layers) states.add(state(id));
        BlockState gateState = gate == null ? null : state(gate);
        for (int i = 0; i < wall.length; i += 2) {
            int x = wall[i], z = wall[i + 1];
            if (x < chunk.minX() || x > chunk.maxX() || z < chunk.minZ() || z > chunk.maxZ()) continue;
            int y = ground(level, x, z);
            Boolean gateAlongZ = gateAt(x, z);
            if (gateAlongZ != null) {
                for (int h = 0; h < states.size(); h++) level.setBlock(new BlockPos(x, y + h, z), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                if (gateState != null) {
                    BlockState g = gateState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                            ? gateState.setValue(BlockStateProperties.HORIZONTAL_FACING, gateAlongZ ? Direction.NORTH : Direction.EAST)
                            : gateState;
                    put(level, new BlockPos(x, y, z), VillagePaletteProcessor.dress(g));
                }
                continue;
            }
            BlockState footing = VillagePaletteProcessor.dress(states.get(0));
            for (int d = 1; d <= FOOTING; d++) {
                BlockPos below = new BlockPos(x, y - d, z);
                BlockState there = level.getBlockState(below);
                if (!there.isAir() && there.getFluidState().isEmpty()) break;
                level.setBlock(below, footing, 2);
            }
            for (int h = 0; h < states.size(); h++) put(level, new BlockPos(x, y + h, z), VillagePaletteProcessor.dress(states.get(h)));
        }
        placeCorners(level, random, chunk);
    }

    private void placeCorners(WorldGenLevel level, RandomSource random, BoundingBox chunk) {
        if (corner == null || corners.length == 0) return;
        var template = level.getLevel().getStructureManager().get(corner).orElse(null);
        if (template == null) return;
        var size = template.getSize();
        for (int i = 0; i < corners.length; i += 2) {
            int x = corners[i] - size.getX() / 2, z = corners[i + 1] - size.getZ() / 2;
            if (x > chunk.maxX() || x + size.getX() < chunk.minX() || z > chunk.maxZ() || z + size.getZ() < chunk.minZ()) continue;
            // The template's ground layer replaces the ground block, one below the first free block.
            int y = ground(level, corners[i], corners[i + 1]) - 1 - cornerGround;
            BlockPos at = new BlockPos(x, y, z);
            template.placeInWorld(level, at, at, new StructurePlaceSettings().setBoundingBox(chunk)
                    .addProcessor(VillagePaletteProcessor.INSTANCE), random, 2);
        }
    }

    /** The first free block above the ground; worldgen keeps its own height map, a finished chunk another. */
    private static int ground(WorldGenLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        return y > level.getMinBuildHeight() ? y : level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
    }

    /** The wall's cells as x, z pairs. */
    public int[] wallCells() {
        return wall.clone();
    }

    /** How many blocks long the wall runs. */
    public int wallLength() {
        return wall.length / 2;
    }

    /** How many blocks of the wall are gateway. */
    public int gateCells() {
        return gates.length / 3;
    }

    /** Where the corner pieces stand, as x, z pairs at their middles. */
    public int[] cornerCells() {
        return corners.clone();
    }

    /** How many corner pieces the wall sets. */
    public int cornerCount() {
        return corners.length / 2;
    }

    private static void put(WorldGenLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, 2);
        Block block = state.getBlock();
        // Fences, walls, bars and gates join their neighbours once the chunk is finished.
        if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof FenceGateBlock) {
            level.getChunk(pos).markPosForPostprocessing(pos);
        }
    }

    private @Nullable Boolean gateAt(int x, int z) {
        for (int i = 0; i < gates.length; i += 3) if (gates[i] == x && gates[i + 1] == z) return gates[i + 2] == 1;
        return null;
    }

    private static BlockState state(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : BuiltInRegistries.BLOCK.get(location).defaultBlockState();
    }

    private static List<long[]> awayFromGates(List<long[]> corners, List<long[]> gates) {
        List<long[]> out = new ArrayList<>();
        for (long[] c : corners) {
            boolean near = false;
            for (long[] g : gates) if (Math.abs(c[0] - g[0]) + Math.abs(c[1] - g[1]) < 6) near = true;
            if (!near) out.add(c);
        }
        return out;
    }

    private static int[] flatten(List<long[]> cells) {
        int[] out = new int[cells.size() * 2];
        for (int i = 0; i < cells.size(); i++) { out[i * 2] = (int) cells.get(i)[0]; out[i * 2 + 1] = (int) cells.get(i)[1]; }
        return out;
    }

    private static BoundingBox bounds(EnclosurePlan.Plan plan, int minY, int maxY) {
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (long[] c : plan.wall()) {
            minX = (int) Math.min(minX, c[0]); maxX = (int) Math.max(maxX, c[0]);
            minZ = (int) Math.min(minZ, c[1]); maxZ = (int) Math.max(maxZ, c[1]);
        }
        // Corner pieces may stand half outside the wall line.
        return new BoundingBox(minX - 8, minY, minZ - 8, maxX + 8, maxY, maxZ + 8);
    }
}
