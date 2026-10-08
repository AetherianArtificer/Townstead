package com.aetherianartificer.townstead.work.station;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bounded, orientation-relative named blocks belonging to one workstation anchor.
 *
 * <p>Local coordinates are {@code [right, up, forward]}. A target never searches the world and
 * never claims a nearby compatible machine: its exact position is derived from the owned anchor,
 * then its authored block/tag contract is verified. This makes the same roles safe for actions,
 * attended incident conditions and diagnostics.</p>
 */
public record StationTargetLayout(String orientationProperty, Map<String, Target> targets) {
    public static final int MAX_OFFSET = 8;

    public record Target(String role, int right, int up, int forward,
                         List<String> blocks, boolean required) {
        public Target {
            blocks = List.copyOf(blocks);
        }
    }

    public enum Problem { MISSING_ORIENTATION, UNLOADED, WRONG_BLOCK, COLLISION }

    public record Diagnostic(ResourceLocation definition, BlockPos owner, String role,
                             BlockPos target, Problem problem, String detail) {}

    public record Resolution(Map<String, List<BlockPos>> roles, List<Diagnostic> diagnostics) {
        public Resolution {
            roles = Map.copyOf(roles);
            diagnostics = List.copyOf(diagnostics);
        }

        public List<BlockPos> role(String role) { return roles.getOrDefault(role, List.of()); }

        public boolean complete() { return diagnostics.isEmpty(); }
    }

    public StationTargetLayout {
        orientationProperty = orientationProperty == null || orientationProperty.isBlank()
                ? "facing" : orientationProperty;
        targets = Map.copyOf(targets);
    }

    public static @Nullable StationTargetLayout parse(@Nullable JsonElement value) {
        if (value == null || !value.isJsonObject()) return null;
        JsonObject root = value.getAsJsonObject();
        String orientation = root.has("orientation_property")
                && root.get("orientation_property").isJsonPrimitive()
                ? root.get("orientation_property").getAsString() : "facing";
        if (orientation.isBlank() || !root.has("roles") || !root.get("roles").isJsonObject()) return null;
        Map<String, Target> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("roles").entrySet()) {
            String role = entry.getKey();
            if (!role.matches("[a-z0-9_./-]+") || !entry.getValue().isJsonObject()) return null;
            JsonObject object = entry.getValue().getAsJsonObject();
            if (!object.has("offset") || !object.get("offset").isJsonArray()
                    || object.getAsJsonArray("offset").size() != 3) return null;
            int[] offset = new int[3];
            for (int index = 0; index < 3; index++) {
                JsonElement coordinate = object.getAsJsonArray("offset").get(index);
                if (!coordinate.isJsonPrimitive() || !coordinate.getAsJsonPrimitive().isNumber()) return null;
                double raw = coordinate.getAsDouble();
                offset[index] = (int) raw;
                if (raw != offset[index] || Math.abs(offset[index]) > MAX_OFFSET) return null;
            }
            List<String> blocks = new ArrayList<>();
            if (object.has("blocks")) {
                if (!object.get("blocks").isJsonArray()) return null;
                for (JsonElement selector : object.getAsJsonArray("blocks")) {
                    if (!selector.isJsonPrimitive() || !selector.getAsJsonPrimitive().isString()) return null;
                    String raw = selector.getAsString();
                    ResourceLocation id = ResourceLocation.tryParse(raw.startsWith("#") ? raw.substring(1) : raw);
                    if (id == null) return null;
                    blocks.add(raw);
                }
            }
            boolean required = !object.has("required") || object.get("required").getAsBoolean();
            parsed.put(role, new Target(role, offset[0], offset[1], offset[2], blocks, required));
        }
        return parsed.isEmpty() ? null : new StationTargetLayout(orientation, parsed);
    }

    public Resolution resolve(Level level, BlockPos owner, ResourceLocation definition) {
        Map<String, List<BlockPos>> roles = new LinkedHashMap<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Direction facing = facing(level.getBlockState(owner));
        boolean needsOrientation = targets.values().stream()
                .anyMatch(target -> target.right() != 0 || target.forward() != 0);
        if (facing == null && needsOrientation) {
            diagnostics.add(new Diagnostic(definition, copy(owner), "owner", copy(owner),
                    Problem.MISSING_ORIENTATION,
                    "owner has no horizontal direction property named '" + orientationProperty + "'"));
            return new Resolution(roles, diagnostics);
        }
        Direction forward = facing == null ? Direction.NORTH : facing;
        Direction right = forward.getClockWise();
        Set<Long> claimed = new LinkedHashSet<>();
        targets.values().stream().sorted(Comparator.comparing(Target::role)).forEach(target -> {
            BlockPos pos = owner.offset(right.getStepX() * target.right() + forward.getStepX() * target.forward(),
                    target.up(), right.getStepZ() * target.right() + forward.getStepZ() * target.forward());
            if (!claimed.add(pos.asLong())) {
                diagnostics.add(new Diagnostic(definition, copy(owner), target.role(), copy(pos),
                        Problem.COLLISION, "two roles resolve to the same owned block"));
                return;
            }
            if (!level.hasChunkAt(pos)) {
                if (target.required()) diagnostics.add(new Diagnostic(definition, copy(owner), target.role(), copy(pos),
                        Problem.UNLOADED, "required target chunk is not loaded"));
                return;
            }
            if (!target.blocks().isEmpty() && !matches(level.getBlockState(pos), target.blocks())) {
                if (target.required()) diagnostics.add(new Diagnostic(definition, copy(owner), target.role(), copy(pos),
                        Problem.WRONG_BLOCK, "target does not satisfy " + target.blocks()));
                return;
            }
            roles.put(target.role(), List.of(copy(pos)));
        });
        roles.putIfAbsent("owner", List.of(copy(owner)));
        return new Resolution(roles, diagnostics);
    }

    private @Nullable Direction facing(BlockState state) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(orientationProperty);
        if (!(property instanceof DirectionProperty directionProperty)) return null;
        Direction value = state.getValue(directionProperty);
        return value.getAxis().isHorizontal() ? value : null;
    }

    private static boolean matches(BlockState state, List<String> selectors) {
        for (String selector : selectors) {
            ResourceLocation id = ResourceLocation.tryParse(selector.startsWith("#")
                    ? selector.substring(1) : selector);
            if (id == null) continue;
            if (selector.startsWith("#") && state.is(TagKey.create(Registries.BLOCK, id))) return true;
            if (!selector.startsWith("#")
                    && id.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()))) {
                return true;
            }
        }
        return false;
    }

    private static BlockPos copy(BlockPos pos) {
        return new BlockPos(pos.getX(), pos.getY(), pos.getZ());
    }
}
