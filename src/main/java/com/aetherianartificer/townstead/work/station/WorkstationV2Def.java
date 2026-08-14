package com.aetherianartificer.townstead.work.station;

import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.reservation.ReservationSpec;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
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
        List<String> recipeSupplies,
        @Nullable JsonElement requiresJson,
        @Nullable BlockCondition requires,
        @Nullable JsonElement readyJson,
        @Nullable BlockCondition ready,
        @Nullable JsonElement behavior,
        @Nullable JsonElement collect,
        @Nullable JsonElement anchor,
        @Nullable BlockSelector anchorSelector,
        @Nullable JsonElement structure,
        @Nullable BlockSelector structureSelector,
        @Nullable JsonElement capacity,
        @Nullable Value capacityValue,
        int capacityPositions,
        @Nullable Value capacityPositionsValue,
        int capacityPerPosition,
        boolean stackPerPosition,
        @Nullable ReservationSpec reservation,
        ShiftEndPolicy shiftEnd) {

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

        List<String> supplies = new ArrayList<>();
        if (json.has("supplies")) {
            if (!json.get("supplies").isJsonArray()) return null;
            for (JsonElement element : json.getAsJsonArray("supplies")) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return null;
                String selector = element.getAsString();
                ResourceLocation supply = ResourceLocation.tryParse(selector.startsWith("#")
                        ? selector.substring(1) : selector);
                if (supply == null) return null;
                supplies.add(selector);
            }
        }

        JsonElement requiresJson = copy(json.get("requires"));
        BlockCondition requires = requiresJson == null ? null : BlockConditions.parse(requiresJson);
        if (requiresJson != null && requires == null) return null;
        ReservationSpec reservation = null;
        if (json.has("reservation")) {
            if (!json.get("reservation").isJsonObject()) return null;
            JsonObject reservationJson = json.getAsJsonObject("reservation");
            if (!"pheno:reserve".equals(
                    net.minecraft.util.GsonHelper.getAsString(reservationJson, "type", ""))) return null;
            reservation = com.aetherianartificer.townstead.pheno.action.types.ReserveActionType
                    .spec(reservationJson);
            if (reservation == null) return null;
        }
        JsonElement readyJson = copy(json.get("ready"));
        BlockCondition ready = readyJson == null ? null : BlockConditions.parse(readyJson);
        if (readyJson != null && ready == null) return null;
        JsonElement behavior = copy(json.get("behavior"));
        if (behavior != null && !validBehavior(behavior)) return null;
        JsonElement collect = copy(json.get("collect"));
        if (collect != null && !validBehavior(collect)) return null;
        JsonElement anchor = copy(json.get("anchor"));
        BlockSelector anchorSelector = anchor == null ? null : BlockSelectors.parse(anchor);
        if (anchor != null && anchorSelector == null) return null;
        JsonElement structure = copy(json.get("structure"));
        BlockSelector structureSelector = structure == null ? null : BlockSelectors.parse(structure);
        if (structure != null && structureSelector == null) return null;
        JsonElement capacity = copy(json.get("capacity"));
        Value capacityValue = null;
        int capacityPositions = 1;
        Value capacityPositionsValue = null;
        int capacityPerPosition = 1;
        boolean stackPerPosition = false;
        if (capacity != null) {
            if (capacity.isJsonObject() && capacity.getAsJsonObject().has("positions")) {
                JsonObject lane = capacity.getAsJsonObject();
                if (lane.has("type") || !lane.has("per_position")
                        || !lane.get("per_position").isJsonPrimitive()) return null;
                JsonElement positionsElement = lane.get("positions");
                capacityPositionsValue = Values.parse(positionsElement);
                if (capacityPositionsValue == null) return null;
                if (positionsElement.isJsonPrimitive()) {
                    if (!positionsElement.getAsJsonPrimitive().isNumber()) return null;
                    double positions = positionsElement.getAsDouble();
                    capacityPositions = (int) positions;
                    if (capacityPositions <= 0 || positions != capacityPositions) return null;
                }
                JsonElement perPosition = lane.get("per_position");
                if (perPosition.getAsJsonPrimitive().isString()) {
                    if (!"stack".equals(perPosition.getAsString())) return null;
                    stackPerPosition = true;
                } else if (!perPosition.getAsJsonPrimitive().isNumber()) return null;
                else {
                    double amount = perPosition.getAsDouble();
                    if (amount <= 0 || amount != (int) amount) return null;
                    capacityPerPosition = (int) amount;
                }
            } else {
                capacityValue = Values.parse(capacity);
                if (capacityValue == null) return null;
            }
        }

        // V2 deliberately has no station-family-specific driver field. A host may keep a generic
        // Pheno reservation alive while its task runs; operational requirements remain pure.
        if (json.has("driver")) return null;

        ShiftEndPolicy shiftEnd = ShiftEndPolicy.FINISH;
        if (json.has("shift_end")) {
            JsonElement value = json.get("shift_end");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) return null;
            shiftEnd = ShiftEndPolicy.parse(value.getAsString());
            if (shiftEnd == null) return null;
        }

        return new WorkstationV2Def(id, Set.copyOf(blocks), List.copyOf(containers),
                List.copyOf(ingredients), List.copyOf(catalysts), List.copyOf(outputs),
                List.copyOf(returns), List.copyOf(previews), List.copyOf(layout),
                List.copyOf(corrections), List.copyOf(supplies),
                requiresJson, requires, readyJson, ready, behavior, collect,
                anchor, anchorSelector, structure, structureSelector,
                capacity, capacityValue, capacityPositions, capacityPositionsValue,
                capacityPerPosition, stackPerPosition, reservation, shiftEnd);
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

    /** Resolves a lane count against the station block; fixed counts retain their cheap accessor. */
    public int capacityPositions(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        if (capacityPositionsValue == null) return capacityPositions;
        double value = capacityPositionsValue.get(
                com.aetherianartificer.townstead.pheno.selector.SelectorContext.ofBlock(level, pos, null));
        return Math.max(0, (int) Math.floor(value));
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

    /** Whether this station's work session owns a generic Pheno entity reservation. */
    public boolean hasReservation() {
        return reservation != null;
    }

    /** A public, event-driven completion predicate for machines without extractable output slots. */
    public boolean isReady(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return ready != null && ready.test(level, pos);
    }

    /** Resolves fixed station supplies through the live item registry, including datapack tags. */
    public List<RecipeIngredient> resolvedSupplies() {
        List<RecipeIngredient> resolved = new ArrayList<>();
        for (String selector : supplySelectors()) {
            LinkedHashSet<ResourceLocation> ids = new LinkedHashSet<>();
            ResourceLocation sourceTag = null;
            if (selector.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(selector.substring(1));
                if (tagId != null) {
                    sourceTag = tagId;
                    BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId)).ifPresent(set ->
                            set.forEach(holder -> holder.unwrapKey().ifPresent(key -> ids.add(key.location()))));
                }
            } else {
                ResourceLocation id = ResourceLocation.tryParse(selector);
                if (id != null && BuiltInRegistries.ITEM.containsKey(id)) ids.add(id);
            }
            if (!ids.isEmpty()) resolved.add(
                    new RecipeIngredient(List.copyOf(ids), 1, sourceTag));
        }
        return List.copyOf(resolved);
    }

    public List<RecipeIngredient> withSupplies(List<RecipeIngredient> publicInputs) {
        List<RecipeIngredient> supplies = resolvedSupplies();
        if (supplies.isEmpty()) return publicInputs;
        List<RecipeIngredient> out = new ArrayList<>(publicInputs.size() + supplies.size());
        out.addAll(publicInputs);
        out.addAll(supplies);
        return List.copyOf(out);
    }

    public List<RecipeIngredient> ordinaryInputs(List<RecipeIngredient> plannedInputs) {
        int supplyCount = Math.min(resolvedSupplies().size(), plannedInputs.size());
        return supplyCount == 0 ? plannedInputs
                : plannedInputs.subList(0, plannedInputs.size() - supplyCount);
    }

    /**
     * Fixed supplies are inferred from the interaction that uses them. The explicit list remains
     * a compatibility input, but an author does not have to name an ignition tool twice merely so
     * planning can gather it before running the Pheno action.
     */
    private List<String> supplySelectors() {
        LinkedHashSet<String> selectors = new LinkedHashSet<>(recipeSupplies);
        for (JsonElement action : actions(behavior)) {
            JsonObject interaction = interactionOf(action);
            if (interaction == null || !uses(interaction, "supply") || !interaction.has("supply")) continue;
            selectors.add(interaction.get("supply").getAsString());
        }
        return List.copyOf(selectors);
    }

    /** Whether the behavior can change transient requirements before ingredients are staged. */
    public boolean hasPreparationAction() {
        for (JsonElement action : actions(behavior)) {
            if (uses(action, "ingredient")) return false;
            if (uses(action, "supply") || uses(action, "empty")) return true;
        }
        return false;
    }

    /**
     * Internal scheduling hint while the V1 state machine is retired. It is derived from public
     * recipe/behavior facts and is never author-facing workstation syntax.
     */
    public StationType schedulingRole(Set<ResourceLocation> recipeTypes) {
        ResourceLocation campfire = ResourceLocation.tryParse("minecraft:campfire_cooking");
        if (recipeTypes.contains(campfire)) return StationType.FIRE_STATION;
        if (behaviorUses("tool") && !behaviorUses("ingredient")) return StationType.CUTTING_BOARD;
        if (!containerSlots.isEmpty() || collectUses("container") || !recipeSupplies.isEmpty()) {
            return StationType.HOT_STATION;
        }
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
                WorkstationDef.Orderable.ALL, false, Set.of(), List.of(), shiftEnd);
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

    public boolean collectUses(String role) {
        if (collect == null || role == null) return false;
        for (JsonElement action : actions(collect)) if (uses(action, role)) return true;
        return false;
    }

    /** Whether an action names the public item/tag contract for its exceptional tool. */
    public boolean hasDeclaredTool() {
        for (JsonElement action : actions(behavior)) {
            JsonObject object = action.getAsJsonObject();
            if (uses(object, "tool") && object.has("tool")) return true;
        }
        return false;
    }

    /** Public item/tag selectors attached to exceptional tool interactions, in authored order. */
    public List<String> declaredToolSelectors() {
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        for (JsonElement action : actions(behavior)) {
            JsonObject interaction = interactionOf(action);
            if (interaction != null && uses(interaction, "tool") && interaction.has("tool")) {
                selectors.add(interaction.get("tool").getAsString());
            }
        }
        return List.copyOf(selectors);
    }

    /** Matches an action's exact item or item tag without knowing which mod declared it. */
    public boolean toolMatches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        for (JsonElement action : actions(behavior)) {
            JsonObject object = action.getAsJsonObject();
            if (uses(object, "tool") && actionToolMatches(object, stack)) return true;
        }
        return false;
    }

    public static boolean actionToolMatches(JsonObject action, ItemStack stack) {
        if (action == null || stack == null || stack.isEmpty() || !action.has("tool")) return false;
        return actionSelectorMatches(action.get("tool").getAsString(), stack);
    }

    public static boolean actionSelectorMatches(String value, ItemStack stack) {
        if (value == null || stack == null || stack.isEmpty()) return false;
        if (value.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(value.substring(1));
            return id != null && stack.is(TagKey.create(Registries.ITEM, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        Item item = id == null ? null : BuiltInRegistries.ITEM.get(id);
        return item != null && stack.is(item);
    }

    private static Iterable<JsonElement> actions(@Nullable JsonElement value) {
        if (value == null) return List.of();
        return value.isJsonArray() ? value.getAsJsonArray() : List.of(value);
    }

    /**
     * Whether processing requires an empty-hand pulse after ingredients have been committed.
     * Preparation actions before an ingredient (closing a silo) are one-shot and do not qualify.
     */
    public boolean hasRepeatableWorkAction() {
        if (behavior == null) return false;
        boolean afterIngredient = !behaviorUses("ingredient");
        Iterable<JsonElement> actions = behavior.isJsonArray()
                ? behavior.getAsJsonArray() : List.of(behavior);
        for (JsonElement action : actions) {
            if (uses(action, "ingredient")) {
                afterIngredient = true;
            } else if (afterIngredient && uses(action, "empty")) {
                return true;
            }
        }
        return false;
    }

    private static boolean uses(JsonElement action, String role) {
        if (!action.isJsonObject()) return false;
        JsonObject object = action.getAsJsonObject();
        if ("pheno:use_block".equals(
                object.has("type") ? object.get("type").getAsString() : "")) {
            return role.equals(object.has("item") ? object.get("item").getAsString() : "empty");
        }
        return object.has("block_action") && uses(object.get("block_action"), role);
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
        if (object.has("condition") && BlockConditions.parse(object.get("condition")) == null) {
            return false;
        }
        if ("pheno:offset".equals(object.get("type").getAsString())) {
            return object.has("block_action") && validAction(object.get("block_action"));
        }
        if (!"pheno:use_block".equals(object.get("type").getAsString())) return false;
        if (object.has("secondary_use")
                && (!object.get("secondary_use").isJsonPrimitive()
                || !object.getAsJsonPrimitive("secondary_use").isBoolean())) return false;
        if (object.has("tool")) {
            if (!object.get("tool").isJsonPrimitive()
                    || !object.getAsJsonPrimitive("tool").isString()) return false;
            String tool = object.get("tool").getAsString();
            ResourceLocation toolId = ResourceLocation.tryParse(tool.startsWith("#")
                    ? tool.substring(1) : tool);
            if (toolId == null || !"tool".equals(
                    object.has("item") ? object.get("item").getAsString() : "empty")) return false;
        }
        if (!object.has("item")) return true;
        String role = object.get("item").getAsString();
        if (!(role.equals("empty") || role.equals("ingredient") || role.equals("tool")
                || role.equals("supply") || role.equals("container"))) return false;
        if (role.equals("supply")) {
            if (!object.has("supply") || !object.get("supply").isJsonPrimitive()) return false;
            String selector = object.get("supply").getAsString();
            return ResourceLocation.tryParse(selector.startsWith("#")
                    ? selector.substring(1) : selector) != null;
        }
        return true;
    }

    /** The transactional interaction nested inside an optional Pheno block-action transform. */
    static @Nullable JsonObject interactionOf(JsonElement action) {
        if (action == null || !action.isJsonObject()) return null;
        JsonObject object = action.getAsJsonObject();
        if ("pheno:use_block".equals(
                object.has("type") ? object.get("type").getAsString() : "")) return object;
        return object.has("block_action") ? interactionOf(object.get("block_action")) : null;
    }

    private static @Nullable JsonElement copy(@Nullable JsonElement element) {
        return element == null || element.isJsonNull() ? null : element.deepCopy();
    }
}
