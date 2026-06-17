package com.aetherianartificer.townstead.pheno.condition.block;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses a block-condition JSON into a {@link BlockCondition}. The Apoli subset with a
 * uniform public API on both branches: {@code block}/{@code in_tag}, {@code block_state},
 * {@code fluid}, {@code exposed_to_sky}, {@code light_level}, {@code height},
 * {@code hardness}, {@code blast_resistance}, {@code slipperiness}, {@code replaceable},
 * {@code movement_blocking}, {@code light_blocking}, {@code water_loggable},
 * {@code block_entity}, {@code distance_from_coordinates}; the Apugli weather/air leaves
 * {@code air}, {@code in_rain}, {@code raining}, {@code thundering}; meta {@code offset} /
 * {@code adjacent}; and {@code and}/{@code or}/{@code constant}. {@code "inverted":true}
 * negates. ({@code material} is deprecated; {@code attachable}/{@code command}/{@code nbt}
 * are deferred.)
 */
public final class BlockConditions {

    private BlockConditions() {}

    @Nullable
    public static BlockCondition parse(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return null;
        JsonObject json = element.getAsJsonObject();
        BlockCondition condition = build(stripNamespace(GsonHelper.getAsString(json, "type", "")), json);
        if (condition == null) return null;
        return GsonHelper.getAsBoolean(json, "inverted", false) ? condition.negate() : condition;
    }

    @Nullable
    private static BlockCondition build(String type, JsonObject json) {
        switch (type) {
            case "block": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "block", ""));
                if (id == null) return null;
                Block block = BuiltInRegistries.BLOCK.get(id);
                return (level, pos) -> level.getBlockState(pos).is(block);
            }
            case "in_tag": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                if (id == null) return null;
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
                return (level, pos) -> level.getBlockState(pos).is(tag);
            }
            case "block_state": {
                String property = GsonHelper.getAsString(json, "property", "");
                String value = GsonHelper.getAsString(json, "value", "");
                if (property.isEmpty()) return null;
                return (level, pos) -> matchesProperty(level.getBlockState(pos), property, value);
            }
            case "fluid": {
                if (json.has("fluid_condition")) {
                    com.aetherianartificer.townstead.pheno.condition.fluid.FluidCondition fluidCondition =
                            com.aetherianartificer.townstead.pheno.condition.fluid.FluidConditions.parse(
                                    json.get("fluid_condition"));
                    if (fluidCondition == null) return null;
                    return (level, pos) -> fluidCondition.test(level.getFluidState(pos));
                }
                String fluid = GsonHelper.getAsString(json, "fluid", "any").toLowerCase(Locale.ROOT);
                TagKey<Fluid> tag = json.has("tag")
                        ? TagKey.create(Registries.FLUID, DataPackLang.parseId(GsonHelper.getAsString(json, "tag", "")))
                        : null;
                return (level, pos) -> {
                    FluidState state = level.getFluidState(pos);
                    if (tag != null) return state.is(tag);
                    return switch (fluid) {
                        case "empty", "none" -> state.isEmpty();
                        case "water" -> state.is(FluidTags.WATER);
                        case "lava" -> state.is(FluidTags.LAVA);
                        default -> !state.isEmpty();
                    };
                };
            }
            case "exposed_to_sky":
                return Level::canSeeSky;
            case "air":
                return (level, pos) -> level.getBlockState(pos).isAir();
            case "in_rain":
                return Level::isRainingAt;
            case "raining":
                return (level, pos) -> level.isRaining();
            case "thundering":
                return (level, pos) -> level.isThundering();
            case "light_level": {
                int min = GsonHelper.getAsInt(json, "min", 0);
                int max = GsonHelper.getAsInt(json, "max", 15);
                return (level, pos) -> {
                    int light = level.getMaxLocalRawBrightness(pos);
                    return light >= min && light <= max;
                };
            }
            case "height": {
                int min = GsonHelper.getAsInt(json, "min", Integer.MIN_VALUE);
                int max = GsonHelper.getAsInt(json, "max", Integer.MAX_VALUE);
                return (level, pos) -> pos.getY() >= min && pos.getY() <= max;
            }
            case "hardness": {
                float min = GsonHelper.getAsFloat(json, "min", -Float.MAX_VALUE);
                float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
                return (level, pos) -> {
                    float h = level.getBlockState(pos).getDestroySpeed(level, pos);
                    return h >= min && h <= max;
                };
            }
            case "blast_resistance": {
                float min = GsonHelper.getAsFloat(json, "min", -Float.MAX_VALUE);
                float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
                return (level, pos) -> {
                    float r = level.getBlockState(pos).getBlock().getExplosionResistance();
                    return r >= min && r <= max;
                };
            }
            case "slipperiness": {
                float min = GsonHelper.getAsFloat(json, "min", -Float.MAX_VALUE);
                float max = GsonHelper.getAsFloat(json, "max", Float.MAX_VALUE);
                return (level, pos) -> {
                    float f = level.getBlockState(pos).getBlock().getFriction();
                    return f >= min && f <= max;
                };
            }
            case "replaceable":
                return (level, pos) -> level.getBlockState(pos).canBeReplaced();
            case "movement_blocking":
                return (level, pos) -> level.getBlockState(pos).blocksMotion();
            case "light_blocking":
                return (level, pos) -> level.getBlockState(pos).getLightBlock(level, pos) > 0;
            case "water_loggable":
                return (level, pos) -> level.getBlockState(pos).getBlock() instanceof SimpleWaterloggedBlock;
            case "block_entity":
                return (level, pos) -> level.getBlockEntity(pos) != null;
            case "distance_from_coordinates": {
                double x = GsonHelper.getAsDouble(json, "x", 0);
                double y = GsonHelper.getAsDouble(json, "y", 0);
                double z = GsonHelper.getAsDouble(json, "z", 0);
                double min = GsonHelper.getAsDouble(json, "min", 0);
                double max = GsonHelper.getAsDouble(json, "max", Double.MAX_VALUE);
                return (level, pos) -> {
                    double d = Math.sqrt(Math.pow(pos.getX() - x, 2) + Math.pow(pos.getY() - y, 2)
                            + Math.pow(pos.getZ() - z, 2));
                    return d >= min && d <= max;
                };
            }
            case "offset": {
                int ox = GsonHelper.getAsInt(json, "x", 0);
                int oy = GsonHelper.getAsInt(json, "y", 0);
                int oz = GsonHelper.getAsInt(json, "z", 0);
                BlockCondition inner = parse(json.get("condition"));
                if (inner == null) return null;
                return (level, pos) -> inner.test(level, pos.offset(ox, oy, oz));
            }
            case "adjacent": {
                BlockCondition inner = parse(json.get("condition"));
                if (inner == null) return null;
                return (level, pos) -> {
                    for (Direction direction : Direction.values()) {
                        if (inner.test(level, pos.relative(direction))) return true;
                    }
                    return false;
                };
            }
            case "and": {
                List<BlockCondition> all = parseList(json);
                if (all == null) return null;
                return (level, pos) -> all.stream().allMatch(c -> c.test(level, pos));
            }
            case "or": {
                List<BlockCondition> any = parseList(json);
                if (any == null) return null;
                return (level, pos) -> any.stream().anyMatch(c -> c.test(level, pos));
            }
            case "constant": {
                boolean value = GsonHelper.getAsBoolean(json, "value", true);
                return (level, pos) -> value;
            }
            default:
                return null;
        }
    }

    @Nullable
    private static List<BlockCondition> parseList(JsonObject json) {
        if (!json.has("conditions") || !json.get("conditions").isJsonArray()) return null;
        List<BlockCondition> out = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("conditions")) {
            BlockCondition condition = parse(element);
            if (condition == null) return null;
            out.add(condition);
        }
        return out.isEmpty() ? null : out;
    }

    private static <T extends Comparable<T>> boolean matchesProperty(BlockState state, String name, String raw) {
        Property<T> property = castProperty(state.getBlock().getStateDefinition().getProperty(name));
        if (property == null) return false;
        return property.getValue(raw).map(value -> state.getValue(property).equals(value)).orElse(false);
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static <T extends Comparable<T>> Property<T> castProperty(@Nullable Property<?> property) {
        return (Property<T>) property;
    }

    private static String stripNamespace(String type) {
        int colon = type.indexOf(':');
        return colon < 0 ? type : type.substring(colon + 1);
    }
}
