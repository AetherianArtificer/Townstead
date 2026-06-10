package com.aetherianartificer.townstead.origin.port;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

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

    private PowerToGeneConverter() {}

    public static void convert(String geneNamespace, ResourceLocation powerId, JsonObject power,
                               List<ConvertedGene> genes, List<Skip> skips) {
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
                if (childId != null) convert(geneNamespace, childId, child, genes, skips);
            }
            return;
        }

        JsonObject gene = buildGene(type, power);
        if (gene == null) {
            skips.add(new Skip(powerId.toString(), type, "no heritable gene mapping (deferred to professions)"));
            return;
        }
        if (power.has("condition")) {
            JsonObject condition = ApoliConditionTranslator.translate(power.getAsJsonObject("condition"));
            if (condition == null) {
                skips.add(new Skip(powerId.toString(), type, "condition outside the supported subset"));
                return;
            }
            gene.add("condition", condition);
        }
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
            // Apugli innate abilities
            case "aerial_affinity": return ability("aerial_affinity");
            case "hover": return ability("hover");
            case "sprinting": return ability("sprinting");
            case "mobs_ignore": return mobsIgnoreGene(power);
            case "modify_damage_taken": return damageGene(power);
            case "modify_healing": return modifierGene(power, "healing");
            case "modify_damage_dealt": return modifierGene(power, "damage_dealt");
            case "modify_break_speed": return modifierGene(power, "break_speed");
            case "prevent_death": return preventGene("death");
            case "prevent_sleep": return preventGene("sleep");
            case "prevent_entity_collision": return preventGene("entity_collision");
            case "prevent_item_use": return power.has("item_condition") ? null : preventGene("item_use");
            case "effect_immunity": return effectImmunityGene(power);
            case "keep_inventory": return keepInventoryGene();
            case "disable_regen": return disableRegenGene();
            case "burn": return burnGene(power);
            case "damage_over_time": return damageOverTimeGene(power);
            case "model_color": return skinToneGene(power);
            case "self_glow":
            case "entity_glow": return glowGene();
            case "active_self": return activeAbilityGene(power);
            case "fire_projectile": return fireProjectileGene(power);
            case "particle": return particleGene(power);
            case "restrict_armor": return restrictArmorGene(power);
            case "entity_group": return entityGroupGene(power);
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
            // Apugli triggers
            case "action_on_jump": return triggerGene("when_jump", "self", power);
            case "action_when_lightning_struck": return triggerGene("when_struck_by_lightning", "self", power);
            case "action_on_equip": return triggerGene("when_equip", "self", power);
            case "action_when_harmed": return triggerGene("when_hurt", "self", power);
            case "action_on_target_death": return triggerGene("when_kill", "self", power);
            case "action_on_target_hurt": return triggerGene("when_attack", "other", power);
            default: return null;
        }
    }

    private static JsonObject base(String type) {
        JsonObject gene = new JsonObject();
        gene.addProperty("type", "townstead_origins:" + type);
        gene.addProperty("dominance", "recessive");
        return gene;
    }

    private static JsonObject ability(String ability) {
        JsonObject gene = base("ability");
        gene.addProperty("category", "ability");
        gene.addProperty("ability", ability);
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
    private static JsonObject modifierGene(JsonObject power, String kind) {
        JsonObject modifier = firstModifier(power);
        if (modifier == null) return null;
        String operation = GsonHelper.getAsString(modifier, "operation", "").toLowerCase(Locale.ROOT);
        // Only multiplicative modifiers map cleanly to our single scalar (mirrors damageGene).
        if (!operation.contains("multiply")) return null;
        float value = modifier.has("value")
                ? GsonHelper.getAsFloat(modifier, "value", 0f)
                : GsonHelper.getAsFloat(modifier, "amount", 0f);
        JsonObject gene = base("modifier");
        gene.addProperty("category", "ability");
        gene.addProperty("modifier", kind);
        gene.addProperty("operation", "multiply");
        gene.addProperty("value", Math.max(0f, 1f + value));
        return gene;
    }

    private static JsonObject preventGene(String what) {
        JsonObject gene = base("prevent");
        gene.addProperty("category", "ability");
        gene.addProperty("what", what);
        return gene;
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
        ignite.addProperty("type", "townstead_origins:ignite");
        ignite.addProperty("seconds", Math.max(1, GsonHelper.getAsInt(power, "burn", 1)));
        JsonObject gene = base("action_over_time");
        gene.addProperty("category", "ability");
        gene.addProperty("interval", 20);
        gene.add("action", ignite);
        return gene;
    }

    private static JsonObject damageOverTimeGene(JsonObject power) {
        JsonObject damage = new JsonObject();
        damage.addProperty("type", "townstead_origins:damage");
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
        action.addProperty("type", "townstead_origins:fire_projectile");
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
        if (damageCondition == null) return null;
        JsonObject gene = base("damage_modifier");
        gene.addProperty("category", "ability");
        gene.addProperty("modifier", Math.max(0f, multiplier));
        if (damageCondition.has("tag")) {
            gene.addProperty("damage_tag", GsonHelper.getAsString(damageCondition, "tag", ""));
            return gene;
        }
        if (damageCondition.has("damage_type")) {
            gene.addProperty("damage_type", GsonHelper.getAsString(damageCondition, "damage_type", ""));
            return gene;
        }
        return null;
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
