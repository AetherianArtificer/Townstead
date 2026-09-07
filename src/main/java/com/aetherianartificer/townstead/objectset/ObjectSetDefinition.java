package com.aetherianartificer.townstead.objectset;

import com.aetherianartificer.townstead.temperature.ThermalStructures;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A furniture set: an anchor block with a bag of other blocks within a small radius. Not a room and
 * not a building; several can share a house or stand in a yard. Recognised the moment the last
 * block lands. Schema {@code townstead:object_set/v1}:
 * <pre>{
 *   "anchor": ["#townstead:thermal/heat_sources"],
 *   "radius": 3,
 *   "requires": { "#townstead:masonry_materials": 4 },
 *   "thermal": { "kind": "warming", "offset": 10, "radius": 6 },
 *   "icon": "minecraft:campfire"
 * }</pre>
 */
public record ObjectSetDefinition(ResourceLocation id, List<Requirement> anchors, int radius,
                                  List<Requirement> requires, @Nullable ThermalStructures.Spec thermal,
                                  @Nullable ResourceLocation icon) {
    public static final String SCHEMA = "townstead:object_set/v1";
    public static final int MAX_RADIUS = 6;

    public record Requirement(String raw, @Nullable ResourceLocation blockId, @Nullable TagKey<Block> tag, int count) {
        public boolean matches(BlockState state) {
            if (tag != null) return state.is(tag);
            return blockId != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(blockId);
        }

        static @Nullable Requirement parse(String selector, int count) {
            if (selector == null || selector.isBlank() || count < 0) return null;
            boolean isTag = selector.startsWith("#");
            ResourceLocation target = ResourceLocation.tryParse(isTag ? selector.substring(1) : selector);
            if (target == null) return null;
            return isTag ? new Requirement(selector, null, TagKey.create(Registries.BLOCK, target), count)
                    : new Requirement(selector, target, null, count);
        }
    }

    public boolean isAnchor(BlockState state) {
        for (Requirement anchor : anchors) if (anchor.matches(state)) return true;
        return false;
    }

    /** True when the block could be part of this set at all, as anchor or member. */
    public boolean touches(BlockState state) {
        if (isAnchor(state)) return true;
        for (Requirement requirement : requires) if (requirement.matches(state)) return true;
        return false;
    }

    public String translationKey() {
        return "object_set." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static @Nullable ObjectSetDefinition parse(ResourceLocation id, JsonObject json) {
        List<Requirement> anchors = new ArrayList<>();
        JsonElement anchorJson = json.get("anchor");
        if (anchorJson == null) return null;
        if (anchorJson.isJsonPrimitive()) {
            Requirement anchor = Requirement.parse(anchorJson.getAsString(), 1);
            if (anchor == null) return null;
            anchors.add(anchor);
        } else if (anchorJson.isJsonArray()) {
            for (JsonElement element : anchorJson.getAsJsonArray()) {
                if (!element.isJsonPrimitive()) return null;
                Requirement anchor = Requirement.parse(element.getAsString(), 1);
                if (anchor == null) return null;
                anchors.add(anchor);
            }
        }
        if (anchors.isEmpty()) return null;

        int radius = Math.max(1, Math.min(MAX_RADIUS, GsonHelper.getAsInt(json, "radius", 3)));
        List<Requirement> requires = new ArrayList<>();
        if (json.has("requires") && json.get("requires").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("requires").entrySet()) {
                if (!entry.getValue().isJsonPrimitive() || !entry.getValue().getAsJsonPrimitive().isNumber()) return null;
                Requirement requirement = Requirement.parse(entry.getKey(), entry.getValue().getAsInt());
                if (requirement == null) return null;
                requires.add(requirement);
            }
        }
        ThermalStructures.Spec thermal = json.has("thermal") && json.get("thermal").isJsonObject()
                ? ThermalStructures.parse(id.toString(), json.getAsJsonObject("thermal")) : null;
        ResourceLocation icon = json.has("icon") ? ResourceLocation.tryParse(GsonHelper.getAsString(json, "icon", "")) : null;
        return new ObjectSetDefinition(id, List.copyOf(anchors), radius, List.copyOf(requires), thermal, icon);
    }
}
