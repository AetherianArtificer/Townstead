package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.selector.BlockSelector;
import com.aetherianartificer.townstead.pheno.selector.BlockSelectors;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.work.recipe.StationType;
import com.aetherianartificer.townstead.work.recipe.RecipeIngredient;
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
        List<Integer> ingredientSlots,
        List<Integer> catalystSlots,
        List<Integer> outputSlots,
        List<Integer> returnSlots,
        List<Integer> previewSlots,
        List<RecipeSlotRole> recipeLayout,
        List<RecipeCorrection> recipeCorrections,
        @Nullable JsonElement requiresJson,
        @Nullable BlockCondition requires,
        @Nullable JsonElement behavior,
        @Nullable JsonElement structure,
        @Nullable BlockSelector structureSelector,
        @Nullable JsonElement capacity,
        @Nullable Value capacityValue,
        int capacityPositions,
        boolean stackPerPosition) {

    public static final String SCHEMA = "townstead:workstation/v2";

    public enum RecipeSlotRole {
        INGREDIENT, CATALYST, RETURN;

        static @Nullable RecipeSlotRole parse(String raw) {
            return switch (raw) {
                case "ingredient" -> INGREDIENT;
                case "catalyst" -> CATALYST;
                case "return" -> RETURN;
                default -> null;
            };
        }
    }

    /** A narrowly scoped correction for a public recipe whose owning machine demonstrably differs. */
    public record RecipeCorrection(ResourceLocation recipe, ResourceLocation output,
                                   @Nullable JsonElement mods, @Nullable JsonElement config) {}

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
        List<Integer> ingredients = new ArrayList<>();
        List<Integer> catalysts = new ArrayList<>();
        List<Integer> outputs = new ArrayList<>();
        List<Integer> returns = new ArrayList<>();
        List<Integer> previews = new ArrayList<>();
        if (json.has("inventory")) {
            if (!json.get("inventory").isJsonObject()) return null;
            JsonObject inventory = json.getAsJsonObject("inventory");
            if (inventory.has("slots")) {
                if (!inventory.get("slots").isJsonObject()) return null;
                JsonObject slots = inventory.getAsJsonObject("slots");
                if (!parseSlots(slots, "containers", containers)) return null;
                if (!parseSlots(slots, "ingredients", ingredients)) return null;
                if (!parseSlots(slots, "catalysts", catalysts)) return null;
                if (!parseSlots(slots, "outputs", outputs)) return null;
                if (!parseSlots(slots, "returns", returns)) return null;
                if (!parseSlots(slots, "preview", previews)) return null;
                Set<Integer> assigned = new LinkedHashSet<>();
                for (List<Integer> role : List.of(containers, ingredients, catalysts, outputs, returns, previews)) {
                    for (int slot : role) if (!assigned.add(slot)) return null;
                }
            }
        }

        List<RecipeSlotRole> layout = new ArrayList<>();
        if (json.has("recipe_layout")) {
            if (!json.get("recipe_layout").isJsonArray()) return null;
            for (JsonElement element : json.getAsJsonArray("recipe_layout")) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
                RecipeSlotRole role = RecipeSlotRole.parse(element.getAsString());
                if (role == null) return null;
                layout.add(role);
            }
        }

        List<RecipeCorrection> corrections = new ArrayList<>();
        if (json.has("recipe_corrections")) {
            if (!json.get("recipe_corrections").isJsonArray()) return null;
            for (JsonElement element : json.getAsJsonArray("recipe_corrections")) {
                if (!element.isJsonObject()) return null;
                JsonObject correction = element.getAsJsonObject();
                ResourceLocation recipe = correction.has("recipe")
                        ? ResourceLocation.tryParse(correction.get("recipe").getAsString()) : null;
                ResourceLocation output = correction.has("output")
                        ? ResourceLocation.tryParse(correction.get("output").getAsString()) : null;
                if (recipe == null || output == null) return null;
                JsonElement config = copy(correction.get("config"));
                if (config != null && !com.aetherianartificer.townstead.data.ConfigGate.valid(config)) return null;
                corrections.add(new RecipeCorrection(recipe, output,
                        copy(correction.get("mods")), config));
            }
        }

        JsonElement requiresJson = copy(json.get("requires"));
        BlockCondition requires = requiresJson == null ? null : BlockConditions.parse(requiresJson);
        if (requiresJson != null && requires == null) return null;
        JsonElement behavior = copy(json.get("behavior"));
        if (behavior != null && !validBehavior(behavior)) return null;
        JsonElement structure = copy(json.get("structure"));
        BlockSelector structureSelector = structure == null ? null : BlockSelectors.parse(structure);
        if (structure != null && structureSelector == null) return null;
        JsonElement capacity = copy(json.get("capacity"));
        Value capacityValue = null;
        int capacityPositions = 1;
        boolean stackPerPosition = false;
        if (capacity != null) {
            if (capacity.isJsonObject() && capacity.getAsJsonObject().has("positions")) {
                JsonObject lane = capacity.getAsJsonObject();
                if (lane.has("type") || !lane.get("positions").isJsonPrimitive()
                        || !lane.get("positions").getAsJsonPrimitive().isNumber()) return null;
                double positions = lane.get("positions").getAsDouble();
                capacityPositions = (int) positions;
                if (capacityPositions <= 0 || !lane.has("per_position")
                        || positions != capacityPositions
                        || !lane.get("per_position").isJsonPrimitive()) return null;
                JsonElement perPosition = lane.get("per_position");
                if (perPosition.getAsJsonPrimitive().isString()) {
                    if (!"stack".equals(perPosition.getAsString())) return null;
                    stackPerPosition = true;
                } else if (!perPosition.getAsJsonPrimitive().isNumber()) return null;
                else {
                    double amount = perPosition.getAsDouble();
                    if (amount <= 0 || amount != (int) amount) return null;
                }
            } else {
                capacityValue = Values.parse(capacity);
                if (capacityValue == null) return null;
            }
        }

        return new WorkstationV2Def(id, Set.copyOf(blocks), List.copyOf(containers),
                List.copyOf(ingredients), List.copyOf(catalysts), List.copyOf(outputs),
                List.copyOf(returns), List.copyOf(previews), List.copyOf(layout),
                List.copyOf(corrections),
                requiresJson, requires, behavior, structure, structureSelector,
                capacity, capacityValue, capacityPositions, stackPerPosition);
    }

    private static boolean parseSlots(JsonObject slots, String key, List<Integer> out) {
        if (!slots.has(key)) return true;
        if (!slots.get(key).isJsonArray()) return false;
        for (JsonElement slot : slots.getAsJsonArray(key)) {
            if (!slot.isJsonPrimitive() || !slot.getAsJsonPrimitive().isNumber()
                    || slot.getAsInt() < 0 || out.contains(slot.getAsInt())) return false;
            out.add(slot.getAsInt());
        }
        return true;
    }

    public RecipeSlotRole recipeRole(int ingredientIndex) {
        return ingredientIndex >= 0 && ingredientIndex < recipeLayout.size()
                ? recipeLayout.get(ingredientIndex) : RecipeSlotRole.INGREDIENT;
    }

    /** Removes recipe-view-only return entries before planning and gathering ingredients. */
    public List<RecipeIngredient> executableInputs(List<RecipeIngredient> publicInputs) {
        if (recipeLayout.isEmpty()) return publicInputs;
        List<RecipeIngredient> out = new ArrayList<>();
        for (int i = 0; i < publicInputs.size(); i++) {
            if (recipeRole(i) != RecipeSlotRole.RETURN) out.add(publicInputs.get(i));
        }
        return List.copyOf(out);
    }

    public boolean hasExplicitIngredientSlots() {
        return !ingredientSlots.isEmpty() || !catalystSlots.isEmpty();
    }

    public ResourceLocation correctedOutput(ResourceLocation recipe, ResourceLocation fallback) {
        for (RecipeCorrection correction : recipeCorrections) {
            if (!correction.recipe().equals(recipe)) continue;
            if (correction.mods() != null) {
                try {
                    if (!Boolean.TRUE.equals(ModGate.evaluate(correction.mods()))) continue;
                } catch (RuntimeException ignored) {
                    continue;
                }
            }
            if (correction.config() != null && !Boolean.TRUE.equals(
                    com.aetherianartificer.townstead.data.ConfigGate.evaluate(correction.config()))) continue;
            return correction.output();
        }
        return fallback;
    }

    public boolean reservedForInsertion(int slot) {
        return containerSlots.contains(slot) || outputSlots.contains(slot)
                || returnSlots.contains(slot) || previewSlots.contains(slot);
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
                // A V2 recipe family is attached to this exact block. That public association is
                // already the author's statement that the block performs the recipe; requiring a
                // second, manually maintained output-item tag would make the order catalogue a
                // different (and inevitably stale) source of truth from the work engine.
                WorkstationDef.Orderable.ALL, false, Set.of(), List.of());
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
