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
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/heat_sources"));
    public static final TagKey<Block> COOLING_SOURCES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/cooling_sources"));
    public static final TagKey<Block> INSULATING_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/insulating_blocks"));
    public static final TagKey<Block> LEAKY_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "thermal/leaky_blocks"));
    //?} else {
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
    private ThermalBlocks() {}

    /** One mutually exclusive, state-aware source verdict; zero also means inactive. */
    public static int source(net.minecraft.world.level.Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0;
        for (com.aetherianartificer.townstead.compat.temperature.AmbientTemperatureBridge bridge
                : com.aetherianartificer.townstead.compat.temperature.TemperatureBridgeResolver.installed()) {
            float opinion = bridge.blockTemperatureCelsius(level, pos, state);
            if (Float.isFinite(opinion)) return opinion == 0f ? 0 : opinion > 0 ? 1 : -1;
        }
        return taggedSource(state);
    }

    /** Townstead tag fallback. Cooling wins overlapping tags (for example soul campfires). */
    public static int taggedSource(BlockState state) {
        if (!isActive(state)) return 0;
        if (state.is(COOLING_SOURCES)) return -1;
        return state.is(HEAT_SOURCES) ? 1 : 0;
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
     * the position, with linear falloff. Both kinds can apply at once.
     */
    public static float sourceOffset(ServerLevel level, BlockPos center, TemperatureSettings settings) {
        int radius = settings.sourceRadius();
        if (radius <= 0) return 0f;
        double nearestHeat = Double.MAX_VALUE;
        double nearestCool = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -VERTICAL_REACH; dy <= VERTICAL_REACH; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    double distSq = dx * dx + dy * dy + dz * dz;
                    int source = source(level, cursor, state);
                    if (source > 0) {
                        if (distSq < nearestHeat) nearestHeat = distSq;
                    } else if (source < 0 && distSq < nearestCool) {
                        nearestCool = distSq;
                    }
                }
            }
        }
        float offset = 0f;
        if (nearestHeat != Double.MAX_VALUE) offset += settings.heatSourceOffset() * falloff(nearestHeat, radius);
        if (nearestCool != Double.MAX_VALUE) offset += settings.coolingSourceOffset() * falloff(nearestCool, radius);
        return offset;
    }

    private static float falloff(double distSq, int radius) {
        double dist = Math.sqrt(distSq);
        return (float) Math.max(0.0, 1.0 - dist / (radius + 1.0));
    }

    /**
     * Up to {@code limit} nearest sources of one kind within the radius, nearest first. A heat
     * source may be required to be under cover, since a campfire in the snow is not a hearth.
     */
    public static List<BlockPos> nearest(ServerLevel level, BlockPos center, int radius, boolean heat,
                                         boolean requireSheltered, int limit) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) continue;
                    BlockState state = level.getBlockState(cursor);
                    int source = source(level, cursor, state);
                    boolean match = heat ? source > 0 : source < 0;
                    if (!match) continue;
                    if (requireSheltered && level.canSeeSky(cursor.above())) continue;
                    found.add(cursor.immutable());
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        return found.size() > limit ? new ArrayList<>(found.subList(0, limit)) : found;
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
                    if (distSq >= bestDist || distSq < 1) continue;
                    if (!level.getBlockState(cursor.above()).isAir()
                            || isHeatSource(level, cursor.below(), level.getBlockState(cursor.below()))
                            || !level.getBlockState(cursor).isAir()
                            || !level.getBlockState(cursor.below()).isSolidRender(level, cursor.below())) continue;
                    if (!test.test(cursor)) continue;
                    best = cursor.immutable();
                    bestDist = distSq;
                }
            }
        }
        return best;
    }
}
