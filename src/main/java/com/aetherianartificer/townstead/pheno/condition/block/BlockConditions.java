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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import com.aetherianartificer.townstead.pheno.data.ScalarData;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.pheno.condition.Comparison;

/**
 * Parses a block-condition JSON into a {@link BlockCondition}. The Apoli subset with a
 * uniform public API on both branches: {@code block}/{@code in_tag}, {@code block_state},
 * {@code fluid}, {@code exposed_to_sky}, {@code light_level}, {@code height},
 * {@code hardness}, {@code blast_resistance}, {@code slipperiness}, {@code block_shape}, {@code replaceable},
 * {@code movement_blocking}, {@code smokey}, {@code light_blocking}, {@code water_loggable},
 * {@code block_entity}, {@code distance_from_coordinates}; the Apugli weather/air leaves
 * {@code air}, {@code in_rain}, {@code raining}, {@code thundering}; meta {@code offset},
 * {@code adjacent}, and {@code block_chain}; and {@code and}/{@code or}/{@code constant}.
 * {@code "inverted":true}
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
                // Resolution belongs to evaluation, when Minecraft's registries are guaranteed to
                // be bootstrapped. Datapack parsing itself remains a data-only operation.
                return (level, pos) -> level.getBlockState(pos).is(BuiltInRegistries.BLOCK.get(id));
            }
            case "in_tag": {
                ResourceLocation id = DataPackLang.parseId(GsonHelper.getAsString(json, "tag", ""));
                if (id == null) return null;
                return (level, pos) -> level.getBlockState(pos).is(TagKey.create(Registries.BLOCK, id));
            }
            case "block_state": {
                String property = GsonHelper.getAsString(json, "property", "");
                String value = GsonHelper.getAsString(json, "value", "");
                boolean passMissing = "pass".equals(GsonHelper.getAsString(json, "if_missing", "fail"));
                if (property.isEmpty()) return null;
                return (level, pos) -> {
                    BlockState state = level.getBlockState(pos);
                    return state.getBlock().getStateDefinition().getProperty(property) == null
                            ? passMissing : matchesProperty(state, property, value);
                };
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
            case "block_shape": {
                if (!json.has("box") || !json.get("box").isJsonArray()) return null;
                JsonArray box = json.getAsJsonArray("box");
                if (box.size() != 6) return null;
                double x1;
                double y1;
                double z1;
                double x2;
                double y2;
                double z2;
                try {
                    x1 = box.get(0).getAsDouble();
                    y1 = box.get(1).getAsDouble();
                    z1 = box.get(2).getAsDouble();
                    x2 = box.get(3).getAsDouble();
                    y2 = box.get(4).getAsDouble();
                    z2 = box.get(5).getAsDouble();
                } catch (RuntimeException ignored) {
                    return null;
                }
                if (!Double.isFinite(x1) || !Double.isFinite(y1) || !Double.isFinite(z1)
                        || !Double.isFinite(x2) || !Double.isFinite(y2) || !Double.isFinite(z2)
                        || x1 >= x2 || y1 >= y2 || z1 >= z2) return null;
                // Block.box is just this conversion, but calling it while parsing eagerly loads
                // BlockBehaviour. Keeping the condition parser data-only also keeps unit tests and
                // data reloads independent of Minecraft's bootstrapped block-state internals.
                VoxelShape region = Shapes.box(
                        x1 / 16.0, y1 / 16.0, z1 / 16.0,
                        x2 / 16.0, y2 / 16.0, z2 / 16.0);
                return (level, pos) -> Shapes.joinIsNotEmpty(
                        region, level.getBlockState(pos).getShape(level, pos), BooleanOp.AND);
            }
            case "replaceable":
                return (level, pos) -> level.getBlockState(pos).canBeReplaced();
            case "movement_blocking":
                return (level, pos) -> level.getBlockState(pos).blocksMotion();
            case "smokey":
                return (level, pos) -> CampfireBlock.isSmokeyPos(level, pos);
            case "light_blocking":
                return (level, pos) -> level.getBlockState(pos).getLightBlock(level, pos) > 0;
            case "water_loggable":
                return (level, pos) -> level.getBlockState(pos).getBlock() instanceof SimpleWaterloggedBlock;
            case "block_entity": {
                String property = GsonHelper.getAsString(json, "property", "");
                if (property.isEmpty()) return (level, pos) -> level.getBlockEntity(pos) != null;
                JsonElement expected = json.get("value");
                JsonElement contains = json.get("contains");
                boolean nonEmpty = GsonHelper.getAsBoolean(json, "non_empty", false);
                if (expected == null && contains == null && !json.has("non_empty")) return null;
                return (level, pos) -> {
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity == null) return false;
                    Object value = readPublicProperty(entity, property);
                    if (value == null) return false;
                    if (contains != null) return containsValue(value, contains);
                    if (json.has("non_empty")) return hasContents(value) == nonEmpty;
                    return matchesJson(value, expected);
                };
            }
            case "block_data": {
                String key = GsonHelper.getAsString(json, "key", "");
                JsonElement expected = json.get("value");
                boolean exists = GsonHelper.getAsBoolean(json, "exists", true);
                if (key.isBlank() || (expected == null && !json.has("exists"))) return null;
                return (level, pos) -> {
                    BlockEntity entity = level.getBlockEntity(pos);
                    if (entity == null) return false;
                    if (expected != null) return ScalarData.matches(entity.getPersistentData(), key, expected);
                    return entity.getPersistentData().contains(key) == exists;
                };
            }
            case "value": {
                Value left = Values.parse(json.get("value"));
                Value right = Values.parse(json.get("compare_to"));
                if (left == null || right == null) return null;
                Comparison comparison = Comparison.parse(GsonHelper.getAsString(json, "comparison", ">="));
                return (level, pos) -> {
                    SelectorContext context = SelectorContext.ofBlock(level, pos, null);
                    return comparison.compare(left.get(context), right.get(context));
                };
            }
            case "config": {
                if (!com.aetherianartificer.townstead.data.ConfigGate.valid(json)) return null;
                return (level, pos) -> Boolean.TRUE.equals(
                        com.aetherianartificer.townstead.data.ConfigGate.evaluate(json, level));
            }
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
            case "block_chain": {
                Direction direction;
                try {
                    direction = Direction.byName(GsonHelper.getAsString(json, "direction", "down"));
                } catch (Throwable ignored) {
                    direction = null;
                }
                if (direction == null) return null;
                int max = GsonHelper.getAsInt(json, "max", 0);
                if (max < 0) return null;
                BlockCondition through = json.has("through") ? parse(json.get("through")) : null;
                BlockCondition end = parse(json.get("end"));
                if (end == null || (json.has("through") && through == null)) return null;
                Direction step = direction;
                return (level, pos) -> {
                    BlockPos cursor = pos.relative(step);
                    for (int crossed = 0; crossed <= max; crossed++) {
                        if (end.test(level, cursor)) return true;
                        if (crossed == max || through == null || !through.test(level, cursor)) return false;
                        cursor = cursor.relative(step);
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

    @Nullable
    private static Object readPublicProperty(BlockEntity entity, String property) {
        String title = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (String name : List.of(property, "get" + title, "is" + title, "has" + title)) {
            try {
                Method method = entity.getClass().getMethod(name);
                if (method.getParameterCount() == 0) return method.invoke(entity);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean matchesJson(Object value, @Nullable JsonElement expected) {
        if (expected == null || expected.isJsonNull()) return value == null;
        if (value instanceof Number number && expected.isJsonPrimitive()
                && expected.getAsJsonPrimitive().isNumber()) {
            return Double.compare(number.doubleValue(), expected.getAsDouble()) == 0;
        }
        if (value instanceof Boolean bool && expected.isJsonPrimitive()
                && expected.getAsJsonPrimitive().isBoolean()) return bool == expected.getAsBoolean();
        if (value instanceof ResourceLocation id && expected.isJsonPrimitive()) {
            return id.toString().equals(expected.getAsString());
        }
        if (value instanceof ItemStack stack && expected.isJsonPrimitive()) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return id != null && id.toString().equals(expected.getAsString());
        }
        return expected.isJsonPrimitive() && String.valueOf(value).equals(expected.getAsString());
    }

    private static boolean containsValue(Object value, JsonElement expected) {
        if (value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                if (matchesJson(Array.get(value, i), expected)) return true;
            }
            return false;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) if (matchesJson(element, expected)) return true;
            return false;
        }
        return false;
    }

    private static boolean hasContents(Object value) {
        if (value instanceof ItemStack stack) return !stack.isEmpty();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value.getClass().isArray()) return Array.getLength(value) > 0;
        return true;
    }
}
