package com.aetherianartificer.townstead.ritual;

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
import java.util.List;
import java.util.Locale;

/**
 * A ceremony performed at a place: who takes part, what each of them does and when, and what it
 * makes of the candidate. The steps are a timeline in ticks; a step names the role that plays it,
 * a performance clip, and optionally a channel (to layer over a held pose), a sound and a line.
 *
 * <pre>{@code
 * { "schema": "townstead:ritual/v1",
 *   "place": { "block": "townstead:oath_altar", "radius": 12 },
 *   "officiant": { "office": "townstead:lodge_master" },
 *   "candidate": { "condition": { ... } },
 *   "witnesses": { "max": 6 },
 *   "duration": 320,
 *   "steps": [ { "at": 0, "role": "all", "clip": "townstead_performance:formal_stance", "ticks": 40 }, ... ],
 *   "offering": { "accepts": "#townstead:blessable", "blessing": { "against": ["vampire", "wild_vampire"] } },
 *   "outcome": { "join_order": true, "chronicle": "townstead:oath_sworn" } }
 * }</pre>
 */
public record RitualDefinition(ResourceLocation id, ResourceLocation placeBlock, double radius,
                               @Nullable ResourceLocation officiantOffice, Condition candidateCondition,
                               int maxWitnesses, int duration, List<Step> steps,
                               @Nullable Offering offering, Outcome outcome) {
    public static final String SCHEMA = "townstead:ritual/v1";

    public enum Role { ALL, OFFICIANT, CANDIDATE, WITNESSES;
        static Role parse(String raw) {
            try {
                return valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unknown role '" + raw + "'");
            }
        }
    }

    /** One beat: {@code role} plays {@code clip} from tick {@code at} for {@code ticks}. */
    public record Step(int at, Role role, @Nullable ResourceLocation clip, int ticks, String channel,
                       @Nullable ResourceLocation sound, @Nullable String line, List<LineVariant> lines) {}

    /**
     * A line said instead of the step's own when {@code when} holds for the speaker (the
     * candidate is the other side). The condition is parsed on first use.
     */
    public record LineVariant(com.google.gson.JsonElement when, String line) {
        public boolean holds(net.minecraft.world.entity.LivingEntity speaker, @Nullable net.minecraft.world.entity.LivingEntity other) {
            var condition = com.aetherianartificer.townstead.pheno.condition.Conditions.parse(when);
            return condition != null && condition.test(new com.aetherianartificer.townstead.pheno.condition.ConditionContext(speaker, other));
        }
    }

    /** The candidate lays what they hold on the altar; it returns marked against these groups. */
    public record Offering(ResourceLocation accepts, boolean tag, List<String> against) {}

    public record Outcome(boolean joinOrder, @Nullable String chronicle) {}

    public RitualDefinition {
        steps = List.copyOf(steps);
    }

    static RitualDefinition parse(ResourceLocation id, JsonObject json) {
        TownsteadSchema.validateRequired(json, SCHEMA);
        JsonObject place = GsonHelper.getAsJsonObject(json, "place");
        ResourceLocation block = required(GsonHelper.getAsString(place, "block"));
        double radius = GsonHelper.getAsDouble(place, "radius", 12);
        JsonObject officiant = GsonHelper.getAsJsonObject(json, "officiant", new JsonObject());
        ResourceLocation office = officiant.has("office") ? required(GsonHelper.getAsString(officiant, "office")) : null;
        Condition condition = Conditions.ALWAYS;
        JsonObject candidate = GsonHelper.getAsJsonObject(json, "candidate", new JsonObject());
        if (candidate.has("condition")) {
            condition = Conditions.parse(candidate.get("condition"));
            if (condition == null) throw new IllegalArgumentException("'candidate.condition' is not a Pheno condition");
        }
        int witnesses = GsonHelper.getAsInt(GsonHelper.getAsJsonObject(json, "witnesses", new JsonObject()), "max", 0);
        int duration = GsonHelper.getAsInt(json, "duration");
        List<Step> steps = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "steps")) {
            JsonObject step = element.getAsJsonObject();
            int at = GsonHelper.getAsInt(step, "at");
            int ticks = GsonHelper.getAsInt(step, "ticks", 20);
            if (at < 0 || at + ticks > duration) throw new IllegalArgumentException("a step runs past 'duration'");
            steps.add(new Step(at, Role.parse(GsonHelper.getAsString(step, "role")),
                    step.has("clip") ? required(GsonHelper.getAsString(step, "clip")) : null, ticks,
                    GsonHelper.getAsString(step, "channel", "ritual"),
                    step.has("sound") ? required(GsonHelper.getAsString(step, "sound")) : null,
                    step.has("line") ? GsonHelper.getAsString(step, "line") : null, lineVariants(step)));
        }
        Offering offering = null;
        if (json.has("offering")) {
            JsonObject raw = GsonHelper.getAsJsonObject(json, "offering");
            String accepts = GsonHelper.getAsString(raw, "accepts");
            boolean tag = accepts.startsWith("#");
            List<String> against = new ArrayList<>();
            JsonObject blessing = GsonHelper.getAsJsonObject(raw, "blessing", new JsonObject());
            JsonArray groups = GsonHelper.getAsJsonArray(blessing, "against", new JsonArray());
            for (JsonElement group : groups) against.add(group.getAsString());
            offering = new Offering(required(tag ? accepts.substring(1) : accepts), tag, List.copyOf(against));
        }
        JsonObject outcome = GsonHelper.getAsJsonObject(json, "outcome", new JsonObject());
        return new RitualDefinition(id, block, radius, office, condition, witnesses, duration, steps, offering,
                new Outcome(GsonHelper.getAsBoolean(outcome, "join_order", false),
                        outcome.has("chronicle") ? GsonHelper.getAsString(outcome, "chronicle") : null));
    }

    private static ResourceLocation required(String raw) {
        ResourceLocation id = DataPackLang.parseId(raw);
        if (id == null) throw new IllegalArgumentException("'" + raw + "' is not an id");
        return id;
    }

    private static List<LineVariant> lineVariants(JsonObject step) {
        List<LineVariant> out = new ArrayList<>();
        if (!step.has("lines")) return out;
        for (JsonElement element : GsonHelper.getAsJsonArray(step, "lines")) {
            JsonObject variant = element.getAsJsonObject();
            if (!variant.has("when") || !variant.has("line")) throw new IllegalArgumentException("a line variant needs \"when\" and \"line\"");
            out.add(new LineVariant(variant.get("when"), GsonHelper.getAsString(variant, "line")));
        }
        return List.copyOf(out);
    }
}
