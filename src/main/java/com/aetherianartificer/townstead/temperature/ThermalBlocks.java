package com.aetherianartificer.townstead.temperature;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The blocks villagers feel: {@code #townstead:thermal/heat_sources} and
 * {@code #townstead:thermal/cooling_sources}. Both tags are data; nothing here names a block. A
 * heat source with a {@code lit} property only counts while lit.
 */
public final class ThermalBlocks {
    //? if >=1.21 {
    public static final TagKey<Block> COOKING_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/cooking_sources"));
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/heat_sources"));
    public static final TagKey<Block> COOLING_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/cooling_sources"));
    public static final TagKey<Block> INSULATING_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/insulating_blocks"));
    public static final TagKey<Block> LEAKY_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/leaky_blocks"));
    //?} else {
    /*public static final TagKey<Block> COOKING_SOURCES = TagKey.create(Registries.BLOCK,
            new ResourceLocation(Townstead.MOD_ID, "thermal/cooking_sources"));
    *///
    /*public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK,
            new ResourceLocation(Townstead.MOD_ID, "thermal/heat_sources"));
    public static final TagKey<Block> COOLING_SOURCES = TagKey.create(Registries.BLOCK,
            new ResourceLocation(Townstead.MOD_ID, "thermal/cooling_sources"));
    public static final TagKey<Block> INSULATING_BLOCKS = TagKey.create(Registries.BLOCK,
            new ResourceLocation(Townstead.MOD_ID, "thermal/insulating_blocks"));
    public static final TagKey<Block> LEAKY_BLOCKS = TagKey.create(Registries.BLOCK,
            new ResourceLocation(Townstead.MOD_ID, "thermal/leaky_blocks"));
    *///?}

    private static final int VERTICAL_REACH = 2;
    private static final java.util.Map<BlockState, Boolean> MAY_BE_SOURCE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<BlockState, Boolean> MAY_AFFECT_ROOM = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile int generation;
    private ThermalBlocks() {}

    /** One mutually exclusive, state-aware source verdict; zero also means inactive. */
    public static int source(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0;
        float opinion = moddedTemperature(level, pos, state);
        var profile = appliance(state, TemperatureSettings.get());
        if (profile != null) {
            double watts = profile.output(opinion, Float.isFinite(opinion) ? opinion != 0 : isActive(state)).watts();
            return watts > 0 ? 1 : watts < 0 ? -1 : 0;
        }
        if (Float.isFinite(opinion)) return opinion == 0f ? 0 : opinion > 0 ? 1 : -1;
        return taggedSource(state);
    }

    /** Shared by room heat and relief searches, regardless of the selected ambient backend. */
    public static float moddedTemperature(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        for (com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge bridge
                : com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.installed()) {
            float opinion = bridge.blockTemperatureCelsius(level, pos, state);
            if (Float.isFinite(opinion)) return opinion;
        }
        return Float.NaN;
    }

    /** Townstead tag fallback, then the kind of block: any fire, campfire, furnace or magma is heat. Cooling wins a block in both tags. */
    public static int taggedSource(BlockState state) {
        if (!isActive(state)) return 0;
        if (state.is(COOLING_SOURCES)) return -1;
        if (state.is(HEAT_SOURCES)) return 1;
        var block = state.getBlock();
        return block instanceof net.minecraft.world.level.block.BaseFireBlock
                || block instanceof net.minecraft.world.level.block.CampfireBlock
                || block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock
                || block instanceof net.minecraft.world.level.block.MagmaBlock ? 1 : 0;
    }

    /**
     * A block's appliance data: its own entry, else the entry of the vanilla block it is a kind of,
     * so a modded campfire or furnace heats like the vanilla one.
     */
    public static @Nullable ThermalAppliance appliance(BlockState state, TemperatureSettings settings) {
        var own = settings.appliance(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
        if (own != null) return own;
        var block = state.getBlock();
        if (block instanceof net.minecraft.world.level.block.CampfireBlock) return settings.appliance("minecraft:campfire");
        if (block instanceof net.minecraft.world.level.block.AbstractFurnaceBlock) return settings.appliance("minecraft:furnace");
        return null;
    }

    public static boolean isActive(BlockState state) {
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            // Brewery ovens retain weak residual heat after burning; OFF emits none.
            if ("heat".equals(property.getName())
                    && "off".equalsIgnoreCase(state.getValue(property).toString())) return false;
            if (property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag
                    && switch (flag.getName()) {
                        case "lit", "powered", "enabled", "active", "on" -> true;
                        default -> false;
                    } && !state.getValue(flag)) return false;
        }
        return true;
    }

    public static boolean isHeatSource(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        return source(level, pos, state) > 0;
    }

    public static boolean isCoolingSource(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        return source(level, pos, state) < 0;
    }

    /**
     * The warmth or chill the nearest sources within the configured radius add to the ambient at
     * the position, using the same visibility, falloff and overlap rules as room radiation.
     */
    public static float sourceOffset(ServerLevel level, BlockPos center, TemperatureSettings settings, float air) {
        int radius = settings.sourceRadius();
        if (radius <= 0) return 0f;
        double[] sums = new double[4]; // heat sum/max, cooling sum/max
        scan(level, center, radius, VERTICAL_REACH, () -> Double.MAX_VALUE, (pos, state) -> {
            float strength = radiantStrength(level, pos, state, settings, air);
            if (strength == 0 || !RoomHeat.visible(level, center, pos)) return;
            double effect = RoomHeatBalance.localExposure(strength, center.distSqr(pos));
            int i = effect > 0 ? 0 : 2;
            sums[i] += Math.abs(effect);
            sums[i + 1] = Math.max(sums[i + 1], Math.abs(effect));
        });
        return (float) (RoomHeatBalance.combinedExposure(sums[0], sums[1], settings.radiantMax(), settings.radiantStack())
                - RoomHeatBalance.combinedExposure(sums[2], sums[3], settings.radiantMax(), settings.radiantColdStack()));
    }

    private static float falloff(double distSq, int radius) {
        double dist = Math.sqrt(distSq);
        return (float) Math.max(0.0, 1.0 - dist / (radius + 1.0));
    }

    /** A source's radiant strength at its face, in air at {@code air} degrees; zero when it is not active. */
    static float radiantStrength(net.minecraft.world.level.Level level, BlockPos pos, BlockState state,
                                 TemperatureSettings settings, float air) {
        float nativeEffect = moddedTemperature(level, pos, state);
        var profile = appliance(state, settings);
        if (profile != null) return profile.output(nativeEffect,
                Float.isFinite(nativeEffect) ? nativeEffect != 0 : isActive(state), air).radiantDegrees();
        int sign = taggedSource(state);
        return Float.isFinite(nativeEffect) ? nativeEffect
                : sign > 0 ? settings.heatSourceOffset() : sign < 0 ? coolingStrength(settings, air) : 0;
    }

    /** Air at which a tagged cooling source gives its full {@code cooling_source_offset}. */
    static final float COOLING_REFERENCE_AIR = 30f;

    /**
     * Ice and snow sit at 0 C or below: they chill warm air in proportion to the difference and
     * do nothing to air already below freezing, so a snowfield does not chill a winter night.
     */
    public static float coolingStrength(TemperatureSettings settings, float air) {
        return settings.coolingSourceOffset() * Math.max(0, Math.min(1, air / COOLING_REFERENCE_AIR));
    }

    /** For diagnostics: every source in range with its strength, distance, sight line and the heat it gives. */
    public static java.util.List<String> describeSources(ServerLevel level, BlockPos center, TemperatureSettings settings,
                                                         float air, int limit) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        int radius = settings.sourceRadius();
        scan(level, center, radius, VERTICAL_REACH, () -> Double.MAX_VALUE, (pos, state) -> {
            if (lines.size() >= limit) return;
            float strength = radiantStrength(level, pos, state, settings, air);
            boolean sight = RoomHeat.visible(level, center, pos);
            double effect = sight ? RoomHeatBalance.localExposure(strength, center.distSqr(pos)) : 0;
            lines.add(String.format(java.util.Locale.ROOT, "%s at %s: strength %+.1f, %.1f blocks, %s, gives %+.1f C (native %s)",
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()), pos.toShortString(), strength,
                    Math.sqrt(center.distSqr(pos)), sight ? "in sight" : "hidden", effect, moddedTemperature(level, pos, state)));
        });
        return lines;
    }

    /** Up to {@code limit} nearest sources of one kind within the radius, nearest first. */
    public static List<BlockPos> nearest(ServerLevel level, BlockPos center, int radius, boolean heat, int limit) {
        List<BlockPos> found = new ArrayList<>(limit + 1);
        if (limit <= 0) return found;
        Comparator<BlockPos> byDistance = Comparator.comparingDouble(pos -> pos.distSqr(center));
        scan(level, center, radius, 4,
                () -> found.size() < limit ? Double.MAX_VALUE : found.get(limit - 1).distSqr(center), (pos, state) -> {
            if (found.size() == limit && pos.distSqr(center) >= found.get(limit - 1).distSqr(center)) return;
            int source = source(level, pos, state);
            if (heat ? source <= 0 : source >= 0) return;
            BlockPos hit = pos.immutable();
            int at = java.util.Collections.binarySearch(found, hit, byDistance);
            found.add(at < 0 ? -at - 1 : at, hit);
            if (found.size() > limit) found.remove(limit);
        });
        return found;
    }

    /** Whether the state could ever be a source; memoized, since states are few and scans are many. */
    public static boolean mayBeSource(BlockState state) {
        if (state.isAir()) return false;
        Boolean known = MAY_BE_SOURCE.get(state);
        if (known != null) return known;
        boolean may = taggedSource(state) != 0;
        if (!may) {
            for (com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge bridge
                    : com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.installed()) {
                if (bridge.blockMayMatter(state)) { may = true; break; }
            }
        }
        MAY_BE_SOURCE.put(state, may);
        return may;
    }

    /** Tags and backend block data change on reload. */
    /**
     * Whether room heat must look at this state every step: a possible source, an authored
     * appliance, or a thermostat. Everything else in and around a room is skipped once.
     */
    public static boolean mayAffectRoom(BlockState state) {
        if (state.isAir()) return false;
        Boolean known = MAY_AFFECT_ROOM.get(state);
        if (known != null) return known;
        boolean may = mayBeSource(state)
                || state.getBlock() instanceof com.aetherianartificer.townstead.block.RoomThermostatBlock
                || appliance(state, TemperatureSettings.get()) != null;
        MAY_AFFECT_ROOM.put(state, may);
        return may;
    }

    /** Bumped whenever cached per-state verdicts are dropped, so holders of derived data rebuild. */
    public static int generation() {
        return generation;
    }

    public static void clearCache() {
        MAY_BE_SOURCE.clear();
        MAY_AFFECT_ROOM.clear();
        generation++;
        ThermalSourceIndex.clear();
    }

    /**
     * Loaded blocks in the box that may be sources. Uses the section index on the server thread;
     * elsewhere, a direct scan that skips sections whose palette rules them out.
     */
    private static void scan(ServerLevel level, BlockPos center, int radius, int vertical,
                             java.util.function.DoubleSupplier cutoff, ThermalSourceIndex.Visitor visitor) {
        if (ThermalSourceIndex.forEach(level, center, radius, vertical, cutoff, visitor)) return;
        int minX = center.getX() - radius, maxX = center.getX() + radius;
        int minZ = center.getZ() - radius, maxZ = center.getZ() + radius;
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - vertical);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + vertical);
        if (minY > maxY) return;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = minX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = minZ >> 4; cz <= maxZ >> 4; cz++) {
                net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                int x0 = Math.max(minX, cx << 4), x1 = Math.min(maxX, (cx << 4) + 15);
                int z0 = Math.max(minZ, cz << 4), z1 = Math.min(maxZ, (cz << 4) + 15);
                for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
                    net.minecraft.world.level.chunk.LevelChunkSection section =
                            chunk.getSection(level.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(ThermalBlocks::mayBeSource)) continue;
                    int y0 = Math.max(minY, sy << 4), y1 = Math.min(maxY, (sy << 4) + 15);
                    for (int y = y0; y <= y1; y++) {
                        for (int x = x0; x <= x1; x++) {
                            for (int z = z0; z <= z1; z++) {
                                BlockState state = section.getBlockState(x & 15, y & 15, z & 15);
                                if (!mayBeSource(state)) continue;
                                visitor.visit(cursor.set(x, y, z), state);
                            }
                        }
                    }
                }
            }
        }
    }

    /** A standable block near the centre that matches the test, nearest first, or null. */
    public static @Nullable BlockPos nearestStandable(ServerLevel level, BlockPos center, int radius,
                                                      java.util.function.Predicate<BlockPos> test) {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    double distSq = dx * dx + dy * dy + dz * dz;
                    if (distSq >= bestDist) continue;
                    if (!level.getBlockState(cursor.above()).getCollisionShape(level, cursor.above()).isEmpty()
                            || !level.getFluidState(cursor).isEmpty() || !level.getFluidState(cursor.above()).isEmpty()
                            || isHeatSource(level, cursor.below(), level.getBlockState(cursor.below()))
                            || !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()
                            || !level.getBlockState(cursor.below()).isFaceSturdy(level, cursor.below(), net.minecraft.core.Direction.UP)
                            || level.getBlockState(cursor).is(net.minecraft.tags.BlockTags.FIRE)
                            || besideHazard(level, cursor)) continue;
                    if (!test.test(cursor)) continue;
                    best = cursor.immutable();
                    bestDist = distSq;
                }
            }
        }
        return best;
    }

    /** Lava or open flame beside the feet, where a shove or a misstep burns. */
    private static boolean besideHazard(ServerLevel level, BlockPos feet) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockState side = level.getBlockState(feet.relative(direction));
            if (side.is(net.minecraft.tags.BlockTags.FIRE)
                    || side.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) return true;
        }
        return false;
    }
}
