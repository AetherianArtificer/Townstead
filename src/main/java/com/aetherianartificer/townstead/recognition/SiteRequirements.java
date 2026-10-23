package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data-pack site rules for an open-air building, declared as the {@code requires} array of an
 * {@code extended_buildings} entry. MCA's {@code blocks} map can only count ingredients; these
 * predicates describe the ground those ingredients stand on.
 *
 * <p>A type that declares any of them gets its extent from the cells they return, not from a
 * radius: an ingredient belongs to the building when it sits within {@link #LINK} blocks
 * horizontally of a site cell, at any height. That is what keeps a pen to its own paddock while
 * still letting a lantern count from the top of a mast.</p>
 *
 * <p>Kinds:</p>
 * <ul>
 *   <li>{@code surface_over} — a connected walkable surface with the named block (id or
 *       {@code #tag}) in the column beneath it. Names no material, so any deck counts, and
 *       natural ground never does because it rests on the bed of the liquid rather than above it.</li>
 *   <li>{@code enclosed} — ground a grazing animal cannot walk out of (see {@link WalkEnclosure}).
 *       An open field is never an enclosure, and a pen on a hillside is one.</li>
 * </ul>
 */
public final class SiteRequirements {
    /** How far an ingredient may sit from the site it belongs to, horizontally. */
    public static final int LINK = 2;
    private static final int DEFAULT_MAX_DROP = 6;
    private static final int DEFAULT_MAX_INTERIOR = 4096;
    private static final int SEED_REACH = 2;
    private static final int MAX_CELLS = 8192;
    /** A mob's step height: anything this low is walked onto without a jump. */
    private static final double STEP_HEIGHT = 0.6;
    private static final TagKey<Block> NATURAL_SURFACES = TagKey.create(
            Registries.BLOCK, ResourceLocation.tryParse("townstead:natural_surfaces"));
    private static final TagKey<Block> TERRAIN = TagKey.create(
            Registries.BLOCK, ResourceLocation.tryParse("townstead:terrain"));

    public enum Verdict { SATISFIED, UNSATISFIED, UNKNOWN }

    public enum Reason { OPEN, TOO_LARGE, TOO_SMALL }

    /** Why a site is not one, where to look, and the limit it broke, if any. */
    public record Problem(Reason reason, BlockPos at, int limit) {}

    public record Evaluation(Verdict verdict, Set<BlockPos> cells, @Nullable Problem problem) {
        static final Evaluation NONE_REQUIRED = new Evaluation(Verdict.SATISFIED, Set.of(), null);
    }

    /**
     * One pass over the world, shared by every evaluation in it: each block is classified once,
     * and ground already walked out of is never walked again from another seed.
     */
    public static final class Scan implements WalkEnclosure.World {
        final ServerLevel level;
        private final Long2ByteOpenHashMap kinds = new Long2ByteOpenHashMap();
        private final LongOpenHashSet outside = new LongOpenHashSet();
        private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        @Nullable private WalkEnclosure.Bounds bounds;
        @Nullable private Problem problem;
        @Nullable private BlockPos found;

        public Scan(ServerLevel level) {
            this.level = level;
            kinds.defaultReturnValue((byte) -1);
        }

        /** Walks in this scan leave a known site the moment they cross its box. */
        public Scan bounded(@Nullable WalkEnclosure.Bounds bounds) {
            this.bounds = bounds;
            return this;
        }

        public ServerLevel level() {
            return level;
        }

        /** A cell inside the enclosure the last evaluation found, if it found one. */
        public @Nullable BlockPos found() {
            return found;
        }

        @Override
        public int kind(int x, int y, int z) {
            long key = SiteGeometry.pack(x, y, z);
            byte cached = kinds.get(key);
            if (cached >= 0) return cached;
            int kind = classify(x, y, z);
            kinds.put(key, (byte) kind);
            return kind;
        }

        private int classify(int x, int y, int z) {
            cursor.set(x, y, z);
            if (!level.isLoaded(cursor)) return WalkEnclosure.UNLOADED;
            BlockState state = level.getBlockState(cursor);
            // A gate left open still walls a paddock in, so the ring is read from tags.
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                    || state.is(BlockTags.WALLS) || state.is(BlockTags.DOORS)) {
                return WalkEnclosure.BARRIER;
            }
            FluidState fluid = state.getFluidState();
            if (fluid.is(FluidTags.LAVA)) return WalkEnclosure.BARRIER;
            VoxelShape shape = state.getCollisionShape(level, cursor);
            if (shape.isEmpty()) return fluid.is(FluidTags.WATER) ? WalkEnclosure.WATER : WalkEnclosure.OPEN;
            double top = shape.max(Direction.Axis.Y);
            if (top > 1.0) return WalkEnclosure.BARRIER;
            if (top <= STEP_HEIGHT) return WalkEnclosure.LOW;
            return state.is(TERRAIN) ? WalkEnclosure.GROUND : WalkEnclosure.SOLID;
        }

        private void note(Problem found) {
            if (problem == null) problem = found;
        }
    }

    public sealed interface Requirement permits SurfaceOver, Enclosed {
        /** Cells of this site; empty when the requirement is not met, null when not knowable. */
        @Nullable Set<BlockPos> cells(Scan scan, Collection<BlockPos> seeds, int radius);
    }

    /** A walkable surface standing over something: a deck over water, a pier over lava. */
    public record SurfaceOver(@Nullable TagKey<Block> tag, @Nullable ResourceLocation block,
                              int count, int maxDrop) implements Requirement {
        /** True for the block itself and for the fluid a waterlogged block holds. */
        public boolean matches(BlockState state) {
            return matchesPlain(state) || (!state.getFluidState().isEmpty()
                    && matchesPlain(state.getFluidState().createLegacyBlock()));
        }

        private boolean matchesPlain(BlockState state) {
            if (tag != null) return state.is(tag);
            return block != null && block.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        }

        @Override
        public Set<BlockPos> cells(Scan scan, Collection<BlockPos> seeds, int radius) {
            ServerLevel level = scan.level;
            BlockPos origin = seeds.iterator().next();
            Set<Long> cells = SiteGeometry.surface(packed(seeds),
                    (x, y, z) -> isSurfaceOver(level, new BlockPos(x, y, z), this),
                    SEED_REACH, origin.getX(), origin.getZ(), radius, MAX_CELLS);
            return cells.size() < count ? Set.of() : unpacked(cells);
        }
    }

    /** Ground a grazing animal cannot walk out of: a paddock, a yard, a compound. */
    public record Enclosed(int minInterior, int maxInterior) implements Requirement {
        private static final int MAX_ROOTS = 24;

        @Override
        public @Nullable Set<BlockPos> cells(Scan scan, Collection<BlockPos> seeds, int radius) {
            LongArrayList seedCells = new LongArrayList();
            for (BlockPos seed : seeds) seedCells.add(seed.asLong());
            LongList roots = WalkEnclosure.roots(scan, seedCells);
            boolean unknown = false;
            int tried = 0;
            for (int i = 0; i < roots.size(); i++) {
                long root = roots.getLong(i);
                if (scan.outside.contains(root)) continue;
                if (++tried > MAX_ROOTS) break;
                WalkEnclosure.Region region = WalkEnclosure.enclose(scan, root, radius, maxInterior, scan.bounds);
                switch (region.status()) {
                    case UNLOADED -> unknown = true;
                    case FOUND -> {
                        if (region.interior().size() >= minInterior) {
                            scan.found = BlockPos.of(root);
                            return unpacked(region.cells());
                        }
                        scan.note(new Problem(Reason.TOO_SMALL, BlockPos.of(root), minInterior));
                    }
                    case TOO_LARGE -> scan.note(new Problem(Reason.TOO_LARGE, BlockPos.of(root), maxInterior));
                    case ESCAPED -> {
                        // Out of a known site's box is not necessarily out in the world.
                        if (scan.bounds == null) scan.outside.addAll(region.interior());
                        scan.note(new Problem(Reason.OPEN, BlockPos.of(region.gap()), 0));
                    }
                }
            }
            return unknown ? null : Set.of();
        }
    }

    private static List<Long> packed(Collection<BlockPos> positions) {
        List<Long> out = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) out.add(SiteGeometry.pack(pos.getX(), pos.getY(), pos.getZ()));
        return out;
    }

    private static Set<BlockPos> unpacked(Collection<Long> cells) {
        Set<BlockPos> out = new HashSet<>(cells.size());
        for (long cell : cells) {
            out.add(new BlockPos(SiteGeometry.x(cell), SiteGeometry.y(cell), SiteGeometry.z(cell)));
        }
        return out;
    }

    private static volatile Map<String, List<Requirement>> BY_TYPE = Map.of();

    private SiteRequirements() {}

    public static void replaceAll(Map<String, List<Requirement>> next) {
        Map<String, List<Requirement>> stable = new LinkedHashMap<>();
        next.forEach((type, requirements) -> {
            if (type != null && requirements != null && !requirements.isEmpty()) {
                stable.put(type, List.copyOf(requirements));
            }
        });
        BY_TYPE = Map.copyOf(stable);
    }

    public static List<Requirement> of(String buildingType) {
        return buildingType == null ? List.of() : BY_TYPE.getOrDefault(buildingType, List.of());
    }

    /** Whether the type is bounded by an enclosure, which is what makes it a pen. */
    public static boolean isEnclosed(String buildingType) {
        for (Requirement requirement : of(buildingType)) {
            if (requirement instanceof Enclosed) return true;
        }
        return false;
    }

    /** The interior limit of the type's enclosure, or 0 when it has none. */
    public static int maxInterior(String buildingType) {
        for (Requirement requirement : of(buildingType)) {
            if (requirement instanceof Enclosed enclosed) return enclosed.maxInterior();
        }
        return 0;
    }

    public static List<Requirement> parse(JsonArray array) {
        List<Requirement> out = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject json = GsonHelper.convertToJsonObject(element, "requires entry");
            if (json.has("surface_over")) {
                String target = GsonHelper.getAsString(json, "surface_over");
                boolean isTag = target.startsWith("#");
                ResourceLocation id = ResourceLocation.tryParse(isTag ? target.substring(1) : target);
                if (id == null) throw new IllegalArgumentException("invalid 'surface_over' target '" + target + "'");
                int count = GsonHelper.getAsInt(json, "count", 1);
                int maxDrop = GsonHelper.getAsInt(json, "max_drop", DEFAULT_MAX_DROP);
                if (count < 1 || maxDrop < 1) {
                    throw new IllegalArgumentException("'count' and 'max_drop' must be positive");
                }
                out.add(new SurfaceOver(isTag ? TagKey.create(Registries.BLOCK, id) : null,
                        isTag ? null : id, count, maxDrop));
            } else if (GsonHelper.getAsBoolean(json, "enclosed", false)) {
                out.add(new Enclosed(GsonHelper.getAsInt(json, "min_interior", 4),
                        GsonHelper.getAsInt(json, "max_interior", DEFAULT_MAX_INTERIOR)));
            } else if (!BuildingChecks.isCheck(json)) {
                throw new IllegalArgumentException("'requires' entries must declare 'surface_over', 'enclosed', "
                        + "'decoration', 'size', or 'height'");
            }
        }
        return out;
    }

    /**
     * Cells of every requirement of the type, or a verdict saying why there are none.
     * {@code UNKNOWN} means the area is not loaded, which callers must not read as demolition.
     */
    public static Evaluation evaluate(ServerLevel level, String buildingType,
                                      Collection<BlockPos> seeds, BlockPos center, int radius) {
        return level == null ? new Evaluation(Verdict.UNSATISFIED, Set.of(), null)
                : evaluate(new Scan(level), buildingType, seeds, center, radius);
    }

    /** As above, sharing block reads and known outside ground with the rest of the scan. */
    public static Evaluation evaluate(Scan scan, String buildingType,
                                      Collection<BlockPos> seeds, BlockPos center, int radius) {
        List<Requirement> requirements = of(buildingType);
        if (requirements.isEmpty()) return Evaluation.NONE_REQUIRED;
        if (scan == null || center == null || seeds == null || seeds.isEmpty()) {
            return new Evaluation(Verdict.UNSATISFIED, Set.of(), null);
        }
        for (BlockPos seed : seeds) {
            if (!scan.level.isLoaded(seed)) return new Evaluation(Verdict.UNKNOWN, Set.of(), null);
        }
        scan.problem = null;
        scan.found = null;
        Set<BlockPos> all = new HashSet<>();
        for (Requirement requirement : requirements) {
            Set<BlockPos> cells = requirement.cells(scan, seeds, radius);
            if (cells == null) return new Evaluation(Verdict.UNKNOWN, Set.of(), null);
            if (cells.isEmpty()) return new Evaluation(Verdict.UNSATISFIED, Set.of(), scan.problem);
            all.addAll(cells);
        }
        return new Evaluation(Verdict.SATISFIED, all, null);
    }

    /** A solid block a villager could stand on, with the required block in the column beneath. */
    public static boolean isSurfaceOver(ServerLevel level, BlockPos pos, SurfaceOver requirement) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getCollisionShape(level, pos).isEmpty() || state.is(NATURAL_SURFACES)) return false;
        BlockPos above = pos.above();
        if (level.getBlockState(above).isCollisionShapeFullBlock(level, above)) return false;
        for (int dy = 1; dy <= requirement.maxDrop(); dy++) {
            if (requirement.matches(level.getBlockState(pos.below(dy)))) return true;
        }
        return false;
    }
}
