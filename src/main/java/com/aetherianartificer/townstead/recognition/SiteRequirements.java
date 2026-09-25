package com.aetherianartificer.townstead.recognition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

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
 *   <li>{@code enclosed} — ground that a ring of fences, walls or building closes off. The fill
 *       is at one level and fails if it escapes, so an open field is never an enclosure.</li>
 * </ul>
 */
public final class SiteRequirements {
    /** How far an ingredient may sit from the site it belongs to, horizontally. */
    public static final int LINK = 2;
    private static final int DEFAULT_MAX_DROP = 6;
    private static final int DEFAULT_MAX_INTERIOR = 4096;
    private static final int SEED_REACH = 2;
    private static final int MAX_CELLS = 8192;
    private static final TagKey<Block> NATURAL_SURFACES = TagKey.create(
            Registries.BLOCK, ResourceLocation.tryParse("townstead:natural_surfaces"));

    public enum Verdict { SATISFIED, UNSATISFIED, UNKNOWN }

    public record Evaluation(Verdict verdict, Set<BlockPos> cells) {
        static final Evaluation NONE_REQUIRED = new Evaluation(Verdict.SATISFIED, Set.of());
    }

    public sealed interface Requirement permits SurfaceOver, Enclosed {
        /** Cells of this site; empty when the requirement is not met, null when not knowable. */
        @Nullable Set<BlockPos> cells(ServerLevel level, Collection<BlockPos> seeds, int radius);
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
        public Set<BlockPos> cells(ServerLevel level, Collection<BlockPos> seeds, int radius) {
            BlockPos origin = seeds.iterator().next();
            Set<Long> cells = SiteGeometry.surface(packed(seeds),
                    (x, y, z) -> isSurfaceOver(level, new BlockPos(x, y, z), this),
                    SEED_REACH, origin.getX(), origin.getZ(), radius, MAX_CELLS);
            return cells.size() < count ? Set.of() : unpacked(cells);
        }
    }

    /** Ground closed off by a ring: a paddock, a yard, a compound. */
    public record Enclosed(int minInterior, int maxInterior) implements Requirement {
        private static final int MAX_ROOTS = 24;

        @Override
        public @Nullable Set<BlockPos> cells(ServerLevel level, Collection<BlockPos> seeds, int radius) {
            SiteGeometry.CellTest loaded = (x, y, z) -> level.isLoaded(new BlockPos(x, y, z));
            SiteGeometry.CellTest open = (x, y, z) -> isOpen(level, new BlockPos(x, y, z));
            boolean unknown = false;
            int tried = 0;
            for (long root : SiteGeometry.enclosureRoots(packed(seeds), loaded, open)) {
                if (++tried > MAX_ROOTS) break;
                SiteGeometry.Region region = SiteGeometry.enclosure(
                        root, loaded, open, radius, Math.min(maxInterior, MAX_CELLS));
                if (region.status() == SiteGeometry.Status.UNLOADED) unknown = true;
                else if (region.status() == SiteGeometry.Status.FOUND
                        && region.interior() >= minInterior) {
                    return unpacked(region.cells());
                }
            }
            return unknown ? null : Set.of();
        }

        /** A gate left open still walls a paddock in, so the ring is read from tags, not collision. */
        private static boolean isOpen(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                    || state.is(BlockTags.WALLS) || state.is(BlockTags.DOORS)) {
                return false;
            }
            return state.getCollisionShape(level, pos).isEmpty();
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
        List<Requirement> requirements = of(buildingType);
        if (requirements.isEmpty()) return Evaluation.NONE_REQUIRED;
        if (level == null || center == null || seeds == null || seeds.isEmpty()) {
            return new Evaluation(Verdict.UNSATISFIED, Set.of());
        }
        for (BlockPos seed : seeds) {
            if (!level.isLoaded(seed)) return new Evaluation(Verdict.UNKNOWN, Set.of());
        }
        Set<BlockPos> all = new HashSet<>();
        for (Requirement requirement : requirements) {
            Set<BlockPos> cells = requirement.cells(level, seeds, radius);
            if (cells == null) return new Evaluation(Verdict.UNKNOWN, Set.of());
            if (cells.isEmpty()) return new Evaluation(Verdict.UNSATISFIED, Set.of());
            all.addAll(cells);
        }
        return new Evaluation(Verdict.SATISFIED, all);
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

    private static long horizontalDistSqr(BlockPos a, BlockPos b) {
        long dx = a.getX() - b.getX();
        long dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
