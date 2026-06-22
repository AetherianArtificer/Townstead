package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts an Apoli power JSON into a Townstead gene JSON "where they meet": the
 * heritable, passive subset (attributes, abilities, damage/stat modifiers, prevention
 * flags, model colour, glow, self-targeted action triggers). {@code apoli:multiple}
 * expands into its child powers. Anything outside the subset (active/class plumbing,
 * unmappable conditions, "other"-targeting bientity triggers) is recorded as a skip so
 * the porting tool can stub it for the professions system. Offline tool only.
 */
public final class PowerToGeneConverter {

    public record ConvertedGene(ResourceLocation id, JsonObject json, String displayText) {}

    public record Skip(String power, String type, String reason) {}

    private static final java.util.Set<String> MULTIPLE_META =
            java.util.Set.of("type", "condition", "loading_priority", "name", "description",
                    "hidden", "badges", "resource", "data", "id");

    /** Source id the hits-on-target translation references; Apugli's counter has no real power. */
    public static final String HITS_ON_TARGET_COLLECTION = "apugli:hits_on_target";

    private PowerToGeneConverter() {}

    public static void convert(String geneNamespace, ResourceLocation powerId, JsonObject power,
                               List<ConvertedGene> genes, List<Skip> skips,
                               Map<ResourceLocation, JsonObject> recipes) {
        // So power_active conditions can resolve the toggle gene a power converts to.
        ApoliConditionTranslator.geneNamespace = geneNamespace;
        String type = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(power, "type", ""));

        if (type.equals("multiple")) {
            if (power.has("condition")) {
                skips.add(new Skip(powerId.toString(), type, "multiple with a gating condition (split manually)"));
                return;
            }
            for (var entry : power.entrySet()) {
                if (MULTIPLE_META.contains(entry.getKey()) || !entry.getValue().isJsonObject()) continue;
                JsonObject child = entry.getValue().getAsJsonObject();
                if (!child.has("type")) continue;
                ResourceLocation childId = ResourceLocation.tryParse(
                        powerId.getNamespace() + ":" + powerId.getPath() + "/" + entry.getKey());
                if (childId != null) convert(geneNamespace, childId, child, genes, skips, recipes);
            }
            return;
        }

        // A recipe power also emits its embedded recipe as a normal datapack recipe so the
        // gated gene (which gates the output item) has something to match.
        if (type.equals("recipe") && power.has("recipe") && power.get("recipe").isJsonObject()) {
            recipes.put(powerId, power.getAsJsonObject("recipe"));
        }

        JsonObject gene = buildGene(type, power);
        if (gene == null) {
            skips.add(new Skip(powerId.toString(), type, "no heritable gene mapping (deferred to professions)"));
            return;
        }
        if (power.has("condition") && power.get("condition").isJsonObject()) {
            JsonObject condition = ApoliConditionTranslator.translate(power.getAsJsonObject("condition"));
            if (condition != null) {
                gene.add("condition", condition);
            } else {
                // The gene itself maps; only its gate is untranslatable (e.g. a power-active /
                // toggle state). Emit it ungated rather than throwing the whole power away, and
                // flag the dropped condition for the author to re-add.
                skips.add(new Skip(powerId.toString(), type,
                        "converted UNGATED - original condition not translatable, re-add by hand"));
            }
        }
        // Power-id cross-references (a resource/collection's set, a compared resource) point at other
        // powers; rewrite them through the same geneId mapping so they resolve to the imported genes.
        rewriteRefs(gene, geneNamespace);
        // Apugli stores its hits-on-target counter implicitly (no power), so any reference to it needs
        // a backing collection gene synthesized once.
        synthesizeBuiltins(geneNamespace, gene, genes);
        ResourceLocation geneId = geneId(geneNamespace, powerId);
        if (geneId == null) {
            skips.add(new Skip(powerId.toString(), type, "could not derive a gene id"));
            return;
        }
        genes.add(new ConvertedGene(geneId, gene, humanize(powerId)));
    }

    @Nullable
    private static JsonObject buildGene(String type, JsonObject power) {
        switch (type) {
            case "attribute":
            case "modify_attribute":
            case "conditioned_attribute":
                return attributeGene(power);
            case "climbing": return ability("climbing");
            case "water_breathing": return ability("water_breathing");
            case "fire_immunity": return ability("fire_immunity");
            case "night_vision":
            case "toggle_night_vision": return ability("night_vision");
            case "creative_flight": return ability("creative_flight");
            case "elytra_flight": return ability("elytra_flight");
            case "swimming": return ability("swimming");
            case "invisibility": return ability("invisibility");
            case "walk_on_fluid": return ability("walk_on_fluid");
            case "phasing": return ability("phasing");
            case "grounded": return ability("grounded");
            case "modify_falling": return ability("slow_fall");
            case "aerial_affinity": return ability("aerial_affinity");
            case "hover": return ability("hover");
            case "sprinting": return sprintingGene(power);
            case "mobs_ignore": return mobsIgnoreGene(power);
            case "invulnerability": return invulnerabilityGene(power);
            case "modify_damage_taken": return damageGene(power);
            case "modify_healing": return modifierGene(power, "healing");
            case "modify_damage_dealt": return modifierGene(power, "damage_dealt");
            case "modify_break_speed": return modifierGene(power, "break_speed");
            case "modify_jump": return modifierGene(power, "jump");
            case "modify_exhaustion": return modifierGene(power, "exhaustion");
            case "modify_projectile_damage": return modifierGene(power, "projectile_damage");
            case "modify_xp_gain": return modifierGene(power, "xp_gain");
            case "prevent_game_event": return preventGameEventGene(power);
            case "overlay": return overlayGene(power);
            case "prevent_death": return preventGene("death");
            case "prevent_sleep": return preventGene("sleep");
            case "prevent_entity_collision": return preventGene("entity_collision");
            case "prevent_item_use": return preventItemUseGene(power);
            case "effect_immunity": return effectImmunityGene(power);
            case "keep_inventory": return keepInventoryGene();
            case "step_height": return stepHeightGene(power);
            case "entity_set": return collectionGene(power);
            case "disable_regen": return disableRegenGene();
            case "burn": return burnGene(power);
            case "damage_over_time": return damageOverTimeGene(power);
            case "model_color": return skinToneGene(power);
            case "self_glow":
            case "entity_glow": return glowGene();
            case "active_self": return activeAbilityGene(power);
            case "launch": return launchGene(power);
            case "water_vision": return waterVisionGene();
            case "conduit_power_on_land": return periodicEffectGene("minecraft:conduit_power");
            case "fire_projectile": return fireProjectileGene(power);
            case "particle": return particleGene(power);
            case "restrict_armor": return restrictArmorGene(power);
            case "entity_group": return entityGroupGene(power);
            case "toggle": return toggleGene();
            case "inventory": return inventoryGene(power);
            case "recipe": return recipeGene(power);
            case "scare_creepers": return scareMobGene("minecraft:creeper");
            case "modify_harvest": return modifyHarvestGene(power);
            case "attribute_modify_transfer": return likeAirGene(power);
            case "stacking_status_effect": return stackingGene(power);
            // Self-targeted action triggers (use Apoli's entity_action). The bientity
            // "other"-targeting variants need actor/target extraction and are skip-logged.
            case "action_on_land": return triggerGene("when_land", "self", power);
            case "action_on_wake_up": return triggerGene("when_wake_up", "self", power);
            case "action_on_death": return triggerGene("when_death", "self", power);
            case "self_action_on_kill": return triggerGene("when_kill", "self", power);
            case "self_action_when_hit":
            case "action_when_damage_taken": return triggerGene("when_hurt", "self", power);
            case "self_action_on_hit": return triggerGene("when_attack", "self", power);
            case "attacker_action_when_hit": return triggerGene("when_hurt", "other", power);
            case "target_action_on_hit":
            case "action_on_hit": return triggerGene("when_attack", "other", power);
            case "action_on_jump": return triggerGene("when_jump", "self", power);
            case "action_when_lightning_struck": return triggerGene("when_struck_by_lightning", "self", power);
            case "action_on_equip": return triggerGene("when_equip", "self", power);
            case "action_when_harmed": return triggerGene("when_hurt", "self", power);
            case "action_on_target_death": return triggerGene("when_kill", "self", power);
            case "action_on_target_hurt": return triggerGene("when_attack", "other", power);
            case "action_on_item_use": return triggerGene("when_item_use", "self", power);
            default: return null;
        }
    }

    /**
     * step_height -> a pheno:step_height gene from the step-up half (upper_height). The step-down
     * correction (lower_height) and allow_jump_after have no Pheno/vanilla equivalent and are dropped.
     */
    @Nullable
    private static JsonObject stepHeightGene(JsonObject power) {
        double upper = GsonHelper.getAsDouble(power, "upper_height", 0);
        if (upper <= 0) return null;
        JsonObject gene = base("step_height");
        gene.addProperty("category", "ability");
        gene.addProperty("amount", upper);
        return gene;
    }

    /** entity_set -> a pheno:collection (of:entity) gene, porting its add/remove hooks. tick_rate is dropped. */
    private static JsonObject collectionGene(JsonObject power) {
        JsonObject gene = base("collection");
        gene.addProperty("category", "ability");
        gene.addProperty("of", "entity");
        if (power.has("action_on_add")) {
            JsonElement add = ApoliActionTranslator.translateBiEntity(power.get("action_on_add"));
            if (add != null) gene.add("on_add", add);
        }
        if (power.has("action_on_remove")) {
            JsonElement remove = ApoliActionTranslator.translateBiEntity(power.get("action_on_remove"));
            if (remove != null) gene.add("on_remove", remove);
        }
        return gene;
    }

    /** Add backing genes for stores a power references implicitly (Apugli's global hits-on-target). */
    private static void synthesizeBuiltins(String geneNamespace, JsonObject gene, List<ConvertedGene> genes) {
        String hitsId = geneIdString(geneNamespace, HITS_ON_TARGET_COLLECTION);
        if (referencesCollection(gene, hitsId)) addSynthetic(genes, hitsId, hitsOnTargetGene(), "Hits On Target");
    }

    /** The synthesized backing gene for Apugli hits-on-target: a counted entity collection that idles out. */
    private static JsonObject hitsOnTargetGene() {
        JsonObject gene = base("collection");
        gene.addProperty("category", "ability");
        gene.addProperty("of", "entity");
        gene.addProperty("forget_after", 100);
        return gene;
    }

    private static void addSynthetic(List<ConvertedGene> genes, String idString, JsonObject json, String display) {
        ResourceLocation id = ResourceLocation.tryParse(idString);
        if (id == null) return;
        for (ConvertedGene g : genes) if (g.id().equals(id)) return;
        genes.add(new ConvertedGene(id, json, display));
    }

    /** Whether any node in the gene tree has a {@code collection} field pointing at {@code target}. */
    private static boolean referencesCollection(JsonElement element, String target) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) if (referencesCollection(child, target)) return true;
            return false;
        }
        if (!element.isJsonObject()) return false;
        JsonObject obj = element.getAsJsonObject();
        if (obj.has("collection") && obj.get("collection").isJsonPrimitive()
                && target.equals(obj.get("collection").getAsString())) return true;
        for (var entry : obj.entrySet()) if (referencesCollection(entry.getValue(), target)) return true;
        return false;
    }

    private static JsonObject base(String type) {
        JsonObject gene = new JsonObject();
        gene.addProperty("type", "pheno:" + type);
        gene.addProperty("dominance", "recessive");
        return gene;
    }

    private static JsonObject ability(String ability) {
        JsonObject gene = base("ability");
        gene.addProperty("category", "ability");
        gene.addProperty("ability", ability);
        return gene;
    }

    /**
     * sprinting -> the sprinting ability (force-sprint). Apugli's {@code requires_input} (sprint only
     * while moving) becomes a {@code pheno:moving} gate on the ability, which the ability ticker
     * already honors, so it needs no runtime hook.
     */
    private static JsonObject sprintingGene(JsonObject power) {
        JsonObject gene = ability("sprinting");
        if (GsonHelper.getAsBoolean(power, "requires_input", false)) {
            JsonObject moving = new JsonObject();
            moving.addProperty("type", "pheno:moving");
            gene.add("condition", moving);
        }
        return gene;
    }

    private static JsonObject glowGene() {
        JsonObject gene = base("glow");
        gene.addProperty("category", "appearance");
        return gene;
    }

    @Nullable
    private static JsonObject particleGene(JsonObject power) {
        JsonElement particle = power.get("particle");
        String id = null;
        if (particle != null && particle.isJsonPrimitive()) {
            id = particle.getAsString();
        } else if (particle != null && particle.isJsonObject() && particle.getAsJsonObject().has("type")) {
            id = particle.getAsJsonObject().get("type").getAsString();
        }
        if (id == null || id.isEmpty()) return null;
        JsonObject gene = base("particle");
        gene.addProperty("category", "appearance");
        gene.addProperty("particle", id);
        gene.addProperty("count", 1);
        return gene;
    }

    @Nullable
    private static JsonObject restrictArmorGene(JsonObject power) {
        JsonArray slots = new JsonArray();
        for (String slot : new String[]{"head", "chest", "legs", "feet"}) {
            if (power.has(slot)) slots.add(slot);
        }
        if (slots.isEmpty()) return null;
        JsonObject gene = base("restrict_equipment");
        gene.addProperty("category", "ability");
        gene.add("slots", slots);
        return gene;
    }

    private static JsonObject mobsIgnoreGene(JsonObject power) {
        JsonObject gene = base("mobs_ignore");
        gene.addProperty("category", "ability");
        // Both filters are optional and best-effort: a filter that falls outside the
        // supported subset is dropped (the gene still pacifies mobs, just unfiltered).
        if (power.has("mob_condition") && power.get("mob_condition").isJsonObject()) {
            JsonObject mob = ApoliConditionTranslator.translate(power.getAsJsonObject("mob_condition"));
            if (mob != null) gene.add("mob_condition", mob);
        }
        if (power.has("bientity_condition") && power.get("bientity_condition").isJsonObject()) {
            JsonObject bi = ApoliBiEntityConditionTranslator.translate(power.getAsJsonObject("bientity_condition"));
            if (bi != null) gene.add("bientity_condition", bi);
        }
        return gene;
    }

    @Nullable
    private static JsonObject entityGroupGene(JsonObject power) {
        String group = GsonHelper.getAsString(power, "group", "");
        if (group.isEmpty()) return null;
        JsonObject gene = base("entity_group");
        gene.addProperty("category", "ability");
        gene.addProperty("group", group);
        return gene;
    }

    @Nullable
    private static JsonObject preventGameEventGene(JsonObject power) {
        JsonObject gene = base("prevent_game_event");
        gene.addProperty("category", "ability");
        if (power.has("event")) {
            gene.addProperty("event", GsonHelper.getAsString(power, "event", ""));
            return gene;
        }
        if (power.has("events") && power.get("events").isJsonArray()) {
            gene.add("events", power.getAsJsonArray("events"));
            return gene;
        }
        return null;
    }

    private static JsonObject toggleGene() {
        JsonObject gene = base("toggle");
        gene.addProperty("category", "ability");
        gene.addProperty("slot", 0);   // auto-assign a key slot
        return gene;
    }

    @Nullable
    private static JsonObject recipeGene(JsonObject power) {
        JsonObject recipe = power.has("recipe") && power.get("recipe").isJsonObject()
                ? power.getAsJsonObject("recipe") : null;
        if (recipe == null) return null;
        JsonObject result = recipe.has("result") && recipe.get("result").isJsonObject()
                ? recipe.getAsJsonObject("result") : null;
        String item = result == null ? "" : GsonHelper.getAsString(result, "item",
                GsonHelper.getAsString(result, "id", ""));
        if (item.isEmpty()) return null;
        JsonObject gene = base("recipe");
        gene.addProperty("category", "ability");
        gene.addProperty("result", item);
        return gene;
    }

    private static JsonObject inventoryGene(JsonObject power) {
        JsonObject gene = base("inventory");
        gene.addProperty("category", "ability");
        gene.addProperty("size", GsonHelper.getAsInt(power, "size", 27));
        gene.addProperty("drop_on_death", GsonHelper.getAsBoolean(power, "drop_on_death", false));
        gene.addProperty("slot", 0);
        return gene;
    }

    private static JsonObject scareMobGene(String mob) {
        JsonObject gene = base("scare_mob");
        gene.addProperty("category", "ability");
        JsonArray mobs = new JsonArray();
        mobs.add(mob);
        gene.add("mobs", mobs);
        gene.addProperty("radius", 8.0);
        return gene;
    }

    @Nullable
    private static JsonObject modifyHarvestGene(JsonObject power) {
        JsonObject bc = power.has("block_condition") && power.get("block_condition").isJsonObject()
                ? power.getAsJsonObject("block_condition") : null;
        if (bc == null) return null;
        String t = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(bc, "type", ""));
        JsonObject gene = base("modify_harvest");
        gene.addProperty("category", "ability");
        gene.addProperty("allow", GsonHelper.getAsBoolean(power, "allow", true));
        if (t.equals("in_tag") && bc.has("tag")) {
            gene.addProperty("tag", GsonHelper.getAsString(bc, "tag", ""));
            return gene;
        }
        if (t.equals("block") && bc.has("block")) {
            gene.addProperty("block", GsonHelper.getAsString(bc, "block", ""));
            return gene;
        }
        return null;
    }

    @Nullable
    private static JsonObject stackingGene(JsonObject power) {
        if (!power.has("effects") || !power.get("effects").isJsonArray() || power.getAsJsonArray("effects").isEmpty()) {
            return null;
        }
        int maxStacks = GsonHelper.getAsInt(power, "max_stacks", 60);
        JsonObject gene = base("stacking_effect");
        gene.addProperty("category", "ability");
        gene.add("effects", power.getAsJsonArray("effects"));   // same {effect, is_ambient, ...} shape
        gene.addProperty("min_stacks", GsonHelper.getAsInt(power, "min_stacks", 0));
        gene.addProperty("max_stacks", maxStacks);
        // Apoli's duration_per_stack model -> our amplifier model: pick a per-level so the
        // amplifier tops out around 6 at full stacks (author tunes).
        gene.addProperty("stacks_per_level", Math.max(1, maxStacks / 6));
        return gene;
    }

    @Nullable
    private static JsonObject likeAirGene(JsonObject power) {
        String attribute = GsonHelper.getAsString(power, "attribute", "");
        if (attribute.isEmpty()) return null;
        JsonObject gene = base("attribute");
        gene.addProperty("category", "ability");
        gene.addProperty("attribute", attribute);
        gene.addProperty("amount", GsonHelper.getAsFloat(power, "multiplier", 1f));
        gene.addProperty("operation", "multiply_total");
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "pheno:on_ground");
        condition.addProperty("inverted", true);   // i.e. while airborne
        gene.add("condition", condition);
        return gene;
    }

    @Nullable
    private static JsonObject modifierGene(JsonObject power, String kind) {
        JsonObject modifier = firstModifier(power);
        if (modifier == null) return null;
        String operation = GsonHelper.getAsString(modifier, "operation", "addition").toLowerCase(Locale.ROOT);
        float value = modifier.has("value")
                ? GsonHelper.getAsFloat(modifier, "value", 0f)
                : GsonHelper.getAsFloat(modifier, "amount", 0f);
        JsonObject gene = base("modifier");
        gene.addProperty("category", "ability");
        gene.addProperty("modifier", kind);
        // Apoli multiply_base/total -> our multiplicative op (multiply by 1+value); addition ->
        // our additive op (value as-is). Set/other operations don't map to a single scalar.
        if (operation.contains("multiply")) {
            gene.addProperty("operation", "multiply");
            gene.addProperty("value", Math.max(0f, 1f + value));
        } else if (operation.contains("add")) {
            gene.addProperty("operation", "add");
            gene.addProperty("value", value);
        } else {
            return null;
        }
        return gene;
    }

    @Nullable
    private static JsonObject invulnerabilityGene(JsonObject power) {
        JsonObject gene = base("damage_modifier");
        gene.addProperty("category", "ability");
        gene.addProperty("modifier", 0f);   // immune
        JsonObject dc = power.has("damage_condition") && power.get("damage_condition").isJsonObject()
                ? power.getAsJsonObject("damage_condition") : null;
        return applyDamageFilter(gene, dc);
    }

    @Nullable
    private static JsonObject overlayGene(JsonObject power) {
        String texture = GsonHelper.getAsString(power, "texture", "");
        if (texture.isEmpty()) return null;
        JsonObject gene = base("overlay");
        gene.addProperty("category", "appearance");
        gene.addProperty("texture", texture);
        gene.addProperty("alpha", GsonHelper.getAsFloat(power, "strength", 1.0f));
        return gene;
    }

    /** Set a {@code damage_modifier}'s filter from an Apoli damage condition, or {@code null} if it can't. */
    @Nullable
    private static JsonObject applyDamageFilter(JsonObject gene, JsonObject damageCondition) {
        if (damageCondition == null) return null;   // immune-to-everything has no single filter
        String t = ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(damageCondition, "type", ""));
        switch (t) {
            case "in_tag": gene.addProperty("damage_tag", GsonHelper.getAsString(damageCondition, "tag", "")); return gene;
            case "type": gene.addProperty("damage_type", GsonHelper.getAsString(damageCondition, "damage_type", "")); return gene;
            case "fire": gene.addProperty("damage_tag", "minecraft:is_fire"); return gene;
            case "explosive": gene.addProperty("damage_tag", "minecraft:is_explosion"); return gene;
            case "projectile": gene.addProperty("damage_tag", "minecraft:is_projectile"); return gene;
            case "fall": case "from_falling": gene.addProperty("damage_type", "minecraft:fall"); return gene;
            default: return null;
        }
    }

    private static JsonObject preventGene(String what) {
        JsonObject gene = base("prevent");
        gene.addProperty("category", "ability");
        gene.addProperty("what", what);
        return gene;
    }

    /**
     * {@code prevent_item_use} with a simple {@code ingredient} filter (tag/item) becomes a
     * {@code prevent item_use} gene with that filter. Complex filters (the and/or diet logic of
     * carnivore/vegetarian) are skipped rather than converted to a prevent-everything gene; those
     * belong on the diet system.
     */
    @Nullable
    private static JsonObject preventItemUseGene(JsonObject power) {
        if (!power.has("item_condition") || !power.get("item_condition").isJsonObject()) {
            return preventGene("item_use");
        }
        JsonObject ic = power.getAsJsonObject("item_condition");
        if (!ApoliConditionTranslator.stripNamespace(GsonHelper.getAsString(ic, "type", "")).equals("ingredient")) {
            return null;
        }
        JsonObject ingredient = ic.has("ingredient") && ic.get("ingredient").isJsonObject()
                ? ic.getAsJsonObject("ingredient") : ic;
        JsonObject gene = preventGene("item_use");
        if (ingredient.has("tag")) {
            gene.addProperty("tag", GsonHelper.getAsString(ingredient, "tag", ""));
            return gene;
        }
        if (ingredient.has("item")) {
            JsonArray items = new JsonArray();
            items.add(GsonHelper.getAsString(ingredient, "item", ""));
            gene.add("items", items);
            return gene;
        }
        return null;
    }

    @Nullable
    private static JsonObject effectImmunityGene(JsonObject power) {
        JsonArray effects = new JsonArray();
        if (power.has("effect") && power.get("effect").isJsonPrimitive()) {
            effects.add(power.get("effect").getAsString());
        }
        if (power.has("effects") && power.get("effects").isJsonArray()) {
            for (JsonElement el : power.getAsJsonArray("effects")) {
                if (el.isJsonPrimitive()) effects.add(el.getAsString());
            }
        }
        if (effects.isEmpty()) return null;
        JsonObject gene = base("effect_immunity");
        gene.addProperty("category", "ability");
        gene.add("effects", effects);
        return gene;
    }

    private static JsonObject keepInventoryGene() {
        JsonObject gene = base("keep_inventory");
        gene.addProperty("category", "ability");
        return gene;
    }

    private static JsonObject disableRegenGene() {
        JsonObject gene = base("disable_regen");
        gene.addProperty("category", "ability");
        return gene;
    }

    private static JsonObject burnGene(JsonObject power) {
        JsonObject ignite = new JsonObject();
        ignite.addProperty("type", "pheno:ignite");
        ignite.addProperty("seconds", Math.max(1, GsonHelper.getAsInt(power, "burn", 1)));
        JsonObject gene = base("action_over_time");
        gene.addProperty("category", "ability");
        gene.addProperty("interval", 20);
        gene.add("action", ignite);
        return gene;
    }

    private static JsonObject damageOverTimeGene(JsonObject power) {
        JsonObject damage = new JsonObject();
        damage.addProperty("type", "pheno:damage");
        damage.addProperty("amount", GsonHelper.getAsFloat(power, "damage",
                GsonHelper.getAsFloat(power, "damage_amount", 1f)));
        JsonObject gene = base("action_over_time");
        gene.addProperty("category", "ability");
        gene.addProperty("interval", Math.max(10, GsonHelper.getAsInt(power, "interval", 20)));
        gene.add("action", damage);
        return gene;
    }

    @Nullable
    private static JsonObject fireProjectileGene(JsonObject power) {
        JsonObject action = new JsonObject();
        action.addProperty("type", "pheno:fire_projectile");
        if (power.has("entity_type")) action.addProperty("entity", GsonHelper.getAsString(power, "entity_type", ""));
        action.addProperty("speed", GsonHelper.getAsFloat(power, "speed", GsonHelper.getAsFloat(power, "velocity", 1.5f)));
        action.addProperty("inaccuracy", GsonHelper.getAsFloat(power, "divergence", 1.0f));
        JsonObject gene = base("active_ability");
        gene.addProperty("category", "ability");
        gene.add("action", action);
        gene.addProperty("cooldown", GsonHelper.getAsInt(power, "cooldown", 20));
        return gene;
    }

    @Nullable
    private static JsonObject triggerGene(String trigger, String entityTarget, JsonObject power) {
        JsonElement action = ApoliActionTranslator.translate(power.get("entity_action"));
        String target = entityTarget;
        if (action == null) {
            // A bi-entity action is authored from the actor's (bearer's) perspective, so
            // its own actor_action/target_action wrappers carry the orientation.
            action = ApoliActionTranslator.translateBiEntity(power.get("bientity_action"));
            target = "self";
        }
        if (action == null) return null;
        JsonObject gene = base("trigger");
        gene.addProperty("category", "ability");
        gene.addProperty("trigger", trigger);
        gene.addProperty("target", target);
        gene.add("action", action);
        return gene;
    }

    private static JsonObject launchGene(JsonObject power) {
        JsonObject velocity = new JsonObject();
        velocity.addProperty("type", "pheno:add_velocity");
        velocity.addProperty("y", GsonHelper.getAsFloat(power, "speed", GsonHelper.getAsFloat(power, "strength", 1.5f)));
        velocity.addProperty("relative", true);
        JsonObject gene = base("active_ability");
        gene.addProperty("category", "ability");
        gene.add("action", velocity);
        gene.addProperty("cooldown", GsonHelper.getAsInt(power, "cooldown", 20));
        return gene;
    }

    private static JsonObject waterVisionGene() {
        JsonObject gene = ability("night_vision");
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "pheno:in_water");
        gene.add("condition", condition);
        return gene;
    }

    private static JsonObject periodicEffectGene(String effect) {
        JsonObject apply = new JsonObject();
        apply.addProperty("type", "pheno:apply_effect");
        apply.addProperty("effect", effect);
        apply.addProperty("duration", 100);
        JsonObject gene = base("action_over_time");
        gene.addProperty("category", "ability");
        gene.addProperty("interval", 40);
        gene.add("action", apply);
        return gene;
    }

    @Nullable
    private static JsonObject activeAbilityGene(JsonObject power) {
        JsonElement action = ApoliActionTranslator.translate(power.get("entity_action"));
        if (action == null) return null;
        JsonObject gene = base("active_ability");
        gene.addProperty("category", "ability");
        gene.add("action", action);
        gene.addProperty("cooldown", GsonHelper.getAsInt(power, "cooldown", 20));
        return gene;
    }

    @Nullable
    private static JsonObject attributeGene(JsonObject power) {
        JsonObject modifier = firstModifier(power);
        if (modifier == null) return null;
        String attribute = GsonHelper.getAsString(modifier, "attribute", "");
        if (attribute.isEmpty()) return null;
        float amount = modifier.has("value")
                ? GsonHelper.getAsFloat(modifier, "value", 0f)
                : GsonHelper.getAsFloat(modifier, "amount", 0f);
        JsonObject gene = base("attribute");
        gene.addProperty("category", "ability");
        gene.addProperty("attribute", attribute);
        gene.addProperty("amount", amount);
        gene.addProperty("operation", mapOperation(GsonHelper.getAsString(modifier, "operation", "addition")));
        return gene;
    }

    @Nullable
    private static JsonObject firstModifier(JsonObject power) {
        if (power.has("modifier") && power.get("modifier").isJsonObject()) return power.getAsJsonObject("modifier");
        if (power.has("modifiers") && power.get("modifiers").isJsonArray()) {
            var array = power.getAsJsonArray("modifiers");
            if (!array.isEmpty() && array.get(0).isJsonObject()) return array.get(0).getAsJsonObject();
        }
        return null;
    }

    private static String mapOperation(String apoli) {
        String op = apoli.toLowerCase(Locale.ROOT);
        if (op.contains("multiply_total")) return "multiply_total";
        if (op.contains("multiply_base") || op.contains("multiplier_base")) return "multiply_base";
        return "add";
    }

    @Nullable
    private static JsonObject damageGene(JsonObject power) {
        JsonObject modifier = firstModifier(power);
        if (modifier == null) return null;
        String operation = GsonHelper.getAsString(modifier, "operation", "addition").toLowerCase(Locale.ROOT);
        float value = GsonHelper.getAsFloat(modifier, "value", GsonHelper.getAsFloat(modifier, "amount", 0f));
        // Only multiplicative reductions/amplifications map cleanly to a scalar.
        if (!operation.contains("multiply")) return null;
        float multiplier = 1f + value;

        JsonObject damageCondition = power.has("damage_condition") && power.get("damage_condition").isJsonObject()
                ? power.getAsJsonObject("damage_condition") : null;
        JsonObject gene = base("damage_modifier");
        gene.addProperty("category", "ability");
        gene.addProperty("modifier", Math.max(0f, multiplier));
        return applyDamageFilter(gene, damageCondition);
    }

    private static JsonObject skinToneGene(JsonObject power) {
        int r = Math.round(GsonHelper.getAsFloat(power, "red", 1f) * 255f) & 0xFF;
        int g = Math.round(GsonHelper.getAsFloat(power, "green", 1f) * 255f) & 0xFF;
        int b = Math.round(GsonHelper.getAsFloat(power, "blue", 1f) * 255f) & 0xFF;
        JsonObject gene = base("skin_tone");
        gene.addProperty("category", "appearance");
        gene.addProperty("tint", String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b));
        gene.addProperty("blend", "multiply");
        return gene;
    }

    @Nullable
    private static ResourceLocation geneId(String geneNamespace, ResourceLocation powerId) {
        String path = "imported/" + sanitize(powerId.getNamespace()) + "/" + sanitize(powerId.getPath());
        return ResourceLocation.tryParse(geneNamespace + ":" + path);
    }

    /**
     * Fields in a converted gene whose value is another power's id. They become cross-references to
     * the imported gene that power converts to: a resource/collection's backing power
     * ({@code resource}/{@code collection}/the {@code in} of a {@code for_each}) and the second
     * operand of a resource comparison ({@code compared_to_resource}).
     */
    private static final java.util.Set<String> REF_FIELDS =
            java.util.Set.of("resource", "compared_to_resource", "collection", "in");

    /**
     * Rewrites every power-id cross-reference in a converted gene tree through {@link #geneIdString}
     * so it resolves to the imported gene the referenced power became, not the original Apoli id.
     * External references (to powers not in this pack) still rewrite to an imported id that will not
     * exist, exactly as the verbatim id would not have resolved either.
     */
    private static void rewriteRefs(JsonElement element, String geneNamespace) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) rewriteRefs(child, geneNamespace);
            return;
        }
        if (!element.isJsonObject()) return;
        JsonObject obj = element.getAsJsonObject();
        for (String field : REF_FIELDS) {
            if (obj.has(field) && obj.get(field).isJsonPrimitive() && obj.getAsJsonPrimitive(field).isString()) {
                obj.addProperty(field, geneIdString(geneNamespace, obj.get(field).getAsString()));
            }
        }
        for (var entry : obj.entrySet()) rewriteRefs(entry.getValue(), geneNamespace);
    }

    /** The gene-id string a power converts to, for cross-references (e.g. {@code power_active} -&gt; toggle). */
    public static String geneIdString(String geneNamespace, String powerIdString) {
        ResourceLocation powerId = ResourceLocation.tryParse(powerIdString);
        if (powerId == null) return geneNamespace + ":imported/" + sanitize(powerIdString);
        return geneNamespace + ":imported/" + sanitize(powerId.getNamespace()) + "/" + sanitize(powerId.getPath());
    }

    private static String sanitize(String raw) {
        StringBuilder sb = new StringBuilder(raw.length());
        for (char c : raw.toLowerCase(Locale.ROOT).toCharArray()) {
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '/' || c == '.' || c == '_' || c == '-'
                    ? c : '_');
        }
        return sb.toString();
    }

    private static String humanize(ResourceLocation powerId) {
        String path = powerId.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        String[] words = name.replace('_', ' ').split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }
}
