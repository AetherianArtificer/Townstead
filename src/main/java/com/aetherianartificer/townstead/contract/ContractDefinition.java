package com.aetherianartificer.townstead.contract;

import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A contract, from {@code data/<ns>/contract/<id>.json}, schema {@code townstead:contract/v1}. The
 * shape follows MCA: Quests where the two overlap (text values, {@code weight},
 * {@code offer_group}, {@code difficulty}, {@code template.variables}, typed {@code objectives}
 * and {@code rewards}), with Townstead's own types namespaced {@code townstead:}.
 * <pre>
 * {
 *   "schema": "townstead:contract/v1",
 *   "giver": { "pool": "townstead:hunter_lodge" },
 *   "title": { "translate": "contract.townstead.hunter.put_one_down.title" },
 *   "about": { "translate": "contract.townstead.hunter.put_one_down.about", "with": ["{count}"] },
 *   "weight": 3, "offer_group": "hunt", "difficulty": "medium", "time_limit_days": 2,
 *   "when": { "type": "pheno:faction_bond", "kind": "townstead:oath" },
 *   "template": { "variables": { "count": { "kind": "int", "min": 1, "max": 2 } } },
 *   "on_accept": { "type": "pheno:spawn_at_edge", "entity_type": "vampirism:vampire", "count": "{count}" },
 *   "objectives": [ { "type": "townstead:kill_entity", "group": ["vampire", "wild_vampire"], "count": "{count}" } ],
 *   "rewards": [ { "type": "townstead:currency" } ]
 * }
 * </pre>
 * A string that is exactly {@code "{var}"} in the objectives, rewards or actions becomes that
 * variable's value (a number stays a number). In text, {@code {var}} puts in the value and
 * {@code {var_name}} the display name of an item, block or entity. {@code when} is read on the
 * player, with the giver as the other side.
 */
public record ContractDefinition(ResourceLocation id, ResourceLocation pool, ContractText title, ContractText about,
                                 int weight, @Nullable String offerGroup, String difficulty, int days,
                                 @Nullable JsonElement whenJson, Map<String, JsonObject> variables,
                                 List<JsonObject> objectives, @Nullable JsonObject onAccept,
                                 @Nullable JsonObject onTurnIn, List<JsonObject> rewards) {
    public static final String SCHEMA = "townstead:contract/v1";
    static final Set<String> KINDS = Set.of("int", "item", "block", "entity", "text");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)}");

    /** The {@code when} condition, parsed on first use. Null when there is none; fails closed when it does not parse. */
    @Nullable Condition when() {
        if (whenJson == null) return null;
        Condition parsed = Conditions.parse(whenJson);
        return parsed != null ? parsed : ctx -> false;
    }

    public static ContractDefinition parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        JsonObject giver = GsonHelper.getAsJsonObject(json, "giver");
        ResourceLocation pool = DataPackLang.parseId(GsonHelper.getAsString(giver, "pool"));
        if (pool == null) throw new IllegalArgumentException("giver.pool: not a valid id");
        if (!json.has("title")) throw new IllegalArgumentException("title: required");
        ContractText title = ContractText.parse(json.get("title"));
        ContractText about = json.has("about") ? ContractText.parse(json.get("about")) : ContractText.EMPTY;
        String difficulty = GsonHelper.getAsString(json, "difficulty", "medium");
        if (!Set.of("easy", "medium", "hard").contains(difficulty)) throw new IllegalArgumentException("difficulty: easy, medium or hard");

        Map<String, JsonObject> variables = new LinkedHashMap<>();
        JsonObject template = GsonHelper.getAsJsonObject(json, "template", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(template, "variables", new JsonObject()).entrySet()) {
            JsonObject variable = entry.getValue().getAsJsonObject();
            String kind = GsonHelper.getAsString(variable, "kind", "");
            if (!KINDS.contains(kind)) throw new IllegalArgumentException("template.variables." + entry.getKey() + ": unknown kind '" + kind + "'");
            if (entry.getKey().equals("player")) throw new IllegalArgumentException("template.variables: 'player' is reserved");
            if (kind.equals("int") && !variable.has("min")) throw new IllegalArgumentException("template.variables." + entry.getKey() + ": an int needs \"min\"");
            if (Set.of("item", "block", "entity").contains(kind) && !variable.has("ids") && !variable.has("tags")) {
                throw new IllegalArgumentException("template.variables." + entry.getKey() + ": needs \"ids\" or \"tags\"");
            }
            variables.put(entry.getKey(), variable);
        }

        List<JsonObject> objectives = objects(json.get("objectives"), "objectives");
        if (objectives.isEmpty()) throw new IllegalArgumentException("objectives: a contract needs at least one");
        for (int i = 0; i < objectives.size(); i++) {
            String error = ContractObjective.check(objectives.get(i));
            if (error != null) throw new IllegalArgumentException("objectives[" + i + "]: " + error);
        }
        List<JsonObject> rewards = objects(json.get("rewards"), "rewards");
        for (int i = 0; i < rewards.size(); i++) {
            String error = ContractReward.check(rewards.get(i));
            if (error != null) throw new IllegalArgumentException("rewards[" + i + "]: " + error);
        }

        ContractDefinition def = new ContractDefinition(id, pool, title, about,
                Math.max(1, GsonHelper.getAsInt(json, "weight", 1)),
                json.has("offer_group") ? GsonHelper.getAsString(json, "offer_group") : null,
                difficulty, Math.max(1, GsonHelper.getAsInt(json, "time_limit_days", 3)),
                json.get("when"), Map.copyOf(variables), objectives,
                json.has("on_accept") ? GsonHelper.getAsJsonObject(json, "on_accept") : null,
                json.has("on_turn_in") ? GsonHelper.getAsJsonObject(json, "on_turn_in") : null, rewards);
        def.checkPlaceholders(json);
        return def;
    }

    /** Every {@code {var}} and {@code {var_name}} must name a variable (or {@code player}, {@code x}, {@code z}). */
    private void checkPlaceholders(JsonObject json) {
        Matcher matcher = PLACEHOLDER.matcher(json.toString().replace("{{", "").replace("}}", ""));
        while (matcher.find()) {
            String name = matcher.group(1);
            String base = name.endsWith("_name") ? name.substring(0, name.length() - 5) : name;
            if (variables.containsKey(name) || variables.containsKey(base) || Set.of("player", "x", "z").contains(name)) continue;
            throw new IllegalArgumentException("{" + name + "} is not a template variable");
        }
    }

    private static List<JsonObject> objects(@Nullable JsonElement raw, String field) {
        List<JsonObject> out = new ArrayList<>();
        if (raw == null || raw.isJsonNull()) return out;
        if (!raw.isJsonArray()) throw new IllegalArgumentException(field + ": must be a list");
        JsonArray array = raw.getAsJsonArray();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) throw new IllegalArgumentException(field + ": every entry must be an object");
            out.add(element.getAsJsonObject());
        }
        return out;
    }
}
