package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The deliberately small V2 workstation document.
 *
 * <p>The block owns its recipes and its runtime inventory owns its slot layout. A definition only
 * states identity and the facts that cannot be learned from those public contracts: a required
 * world condition, exceptional player-like behavior, an explicit vessel channel, or exceptional
 * structure/capacity semantics.</p>
 */
public record WorkstationV2Def(
        ResourceLocation id,
        Set<ResourceLocation> blocks,
        List<Integer> containerSlots,
        @Nullable JsonElement requiresJson,
        @Nullable BlockCondition requires,
        @Nullable JsonElement behavior,
        @Nullable JsonElement structure,
        @Nullable JsonElement capacity) {

    public static final String SCHEMA = "townstead:workstation/v2";

    static @Nullable WorkstationV2Def parse(ResourceLocation id, JsonObject json) {
        if (id == null || json == null || !json.has("blocks") || !json.get("blocks").isJsonArray()) {
            return null;
        }
        Set<ResourceLocation> blocks = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("blocks")) {
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
            ResourceLocation block = ResourceLocation.tryParse(element.getAsString());
            // V2 identity is exact. Tags cannot own a same-id recipe attachment.
            if (block == null || element.getAsString().startsWith("#")) return null;
            blocks.add(block);
        }
        if (blocks.isEmpty()) return null;

        List<Integer> containers = new ArrayList<>();
        if (json.has("inventory")) {
            if (!json.get("inventory").isJsonObject()) return null;
            JsonObject inventory = json.getAsJsonObject("inventory");
            if (inventory.has("slots")) {
                if (!inventory.get("slots").isJsonObject()) return null;
                JsonObject slots = inventory.getAsJsonObject("slots");
                if (slots.has("containers")) {
                    if (!slots.get("containers").isJsonArray()) return null;
                    for (JsonElement slot : slots.getAsJsonArray("containers")) {
                        if (!slot.isJsonPrimitive() || !slot.getAsJsonPrimitive().isNumber()
                                || slot.getAsInt() < 0) return null;
                        containers.add(slot.getAsInt());
                    }
                }
            }
        }

        JsonElement requiresJson = copy(json.get("requires"));
        BlockCondition requires = requiresJson == null ? null : BlockConditions.parse(requiresJson);
        if (requiresJson != null && requires == null) return null;
        JsonElement behavior = copy(json.get("behavior"));
        if (behavior != null && !validBehavior(behavior)) return null;
        JsonElement structure = copy(json.get("structure"));
        JsonElement capacity = copy(json.get("capacity"));

        return new WorkstationV2Def(id, Set.copyOf(blocks), List.copyOf(containers),
                requiresJson, requires, behavior, structure, capacity);
    }

    public boolean isOperational(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return requires == null || requires.test(level, pos);
    }

    /**
     * Internal scheduling hint while the V1 state machine is retired. It is derived from public
     * recipe/behavior facts and is never author-facing workstation syntax.
     */
    public StationType schedulingRole(Set<ResourceLocation> recipeTypes) {
        ResourceLocation campfire = ResourceLocation.tryParse("minecraft:campfire_cooking");
        if (recipeTypes.contains(campfire)) return StationType.FIRE_STATION;
        if (behaviorUses("tool")) return StationType.CUTTING_BOARD;
        if (!containerSlots.isEmpty()) return StationType.HOT_STATION;
        return StationType.PASSIVE_STATION;
    }

    /** Compatibility view for code which still stores StationType in its task-local state. */
    WorkstationDef legacyView(Set<ResourceLocation> recipeTypes) {
        int container = containerSlots.isEmpty() ? 7 : containerSlots.get(0);
        return new WorkstationDef(id, blocks, List.of(), schedulingRole(recipeTypes), container, 6,
                List.of(), null, 0, 200, false,
                DataDrivenStationAdapter.NAME, Set.of(), List.of(), null, null, List.of(), List.of(),
                WorkstationDef.FurnaceSlots.VANILLA, false, null, false, null,
                WorkstationDef.Orderable.TAGGED, false, Set.of(), List.of());
    }

    public boolean behaviorUses(String role) {
        if (behavior == null || role == null) return false;
        if (behavior.isJsonArray()) {
            for (JsonElement action : behavior.getAsJsonArray()) {
                if (uses(action, role)) return true;
            }
            return false;
        }
        return uses(behavior, role);
    }

    private static boolean uses(JsonElement action, String role) {
        if (!action.isJsonObject()) return false;
        JsonObject object = action.getAsJsonObject();
        return "pheno:use_block".equals(object.has("type") ? object.get("type").getAsString() : "")
                && role.equals(object.has("item") ? object.get("item").getAsString() : "empty");
    }

    private static boolean validBehavior(JsonElement behavior) {
        if (behavior.isJsonArray()) {
            if (behavior.getAsJsonArray().isEmpty()) return false;
            for (JsonElement action : behavior.getAsJsonArray()) if (!validAction(action)) return false;
            return true;
        }
        return validAction(behavior);
    }

    private static boolean validAction(JsonElement action) {
        if (!action.isJsonObject()) return false;
        JsonObject object = action.getAsJsonObject();
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) return false;
        if (!"pheno:use_block".equals(object.get("type").getAsString())) return false;
        if (!object.has("item")) return true;
        String role = object.get("item").getAsString();
        return role.equals("empty") || role.equals("ingredient") || role.equals("tool");
    }

    private static @Nullable JsonElement copy(@Nullable JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.deepCopy();
    }
}
