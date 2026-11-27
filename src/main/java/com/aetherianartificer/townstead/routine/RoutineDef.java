package com.aetherianartificer.townstead.routine;

import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.aetherianartificer.townstead.pheno.action.types.SeekActionType;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.condition.block.BlockCondition;
import com.aetherianartificer.townstead.pheno.condition.block.BlockConditions;
import com.aetherianartificer.townstead.pheno.seek.Seeks;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Something a villager does for themselves in free time, such as bathing, laundry or tidying their home:
 * {@code townstead:routine/v1} data. Its steps run in order; a step goes to the best of its places, stays by its rules,
 * and the routine ends when the steps are done or one finds nowhere to go. Branches inside a step are Pheno's own
 * actions ({@code pheno:if_else}, {@code pheno:choice}).
 *
 * <p>A routine keeps nothing between runs: work that comes back later is a second routine, gated on what is in the
 * world.</p>
 */
public record RoutineDef(ResourceLocation id, int priority, @Nullable Condition condition, boolean claim,
                         @Nullable Progress progress, @Nullable Action start, List<Step> steps, @Nullable Finish finish,
                         int maxTicks, @Nullable Action end) {

    public static final String SCHEMA = "townstead:routine/v1";

    /** A value the routine watches, and which way counts as progress. Changes smaller than {@code by} don't count. */
    public record Progress(Value value, boolean down, double by) {}

    /** Actions that run when the routine ends with at least {@code minProgress}, or with every step done if it has no progress. */
    public record Finish(double minProgress, @Nullable Action action) {}

    /** How long a villager stays: actions on a timer, and what ends the step. */
    public record Stay(int every, int times, @Nullable Action action, @Nullable Condition until, int stalled) {
        boolean endsAtOnce() {
            return action == null && until == null && stalled <= 0;
        }
    }

    /**
     * One place a step can use: a block found {@code within} a place, or, with {@code named}, the place an earlier step of
     * this routine was given that name for. {@code usable} and {@code penalty} are asked as if the villager stood there.
     */
    public record Option(double worth, @Nullable BlockCondition block, @Nullable String named, @Nullable Seeks.Within within,
                         double reach, boolean inFluid, @Nullable Condition usable, @Nullable Value penalty,
                         @Nullable Action onArrive, Stay stay) {}

    public enum Else { STOP, SKIP, ACTIONS }

    /**
     * {@code repeat}: when the step ends at a place it goes on to the next place it finds, until there is none left.
     * {@code name}: the place the step ended at keeps this name, for a later step to come back to.
     */
    public record Step(@Nullable Condition when, List<Option> options, @Nullable Action onArrive, Else orElse,
                       @Nullable Action elseAction, boolean repeat, @Nullable String name) {}

    private static final Set<String> ROUTINE_FIELDS = Set.of("schema", "mods", "priority", "condition", "claim", "progress",
            "start", "steps", "finish", "max_ticks", "end");
    private static final Set<String> STAY_FIELDS = Set.of("every", "times", "action", "until", "stalled");
    private static final Set<String> OPTION_FIELDS = union(STAY_FIELDS, Set.of("worth", "find", "within", "reach", "arrive",
            "condition", "penalty", "on_arrive"));
    private static final Set<String> STEP_FIELDS = union(OPTION_FIELDS, Set.of("when", "options", "else", "repeat", "name"));

    public static RoutineDef parse(ResourceLocation id, JsonObject json) {
        only(json, ROUTINE_FIELDS, "");
        int priority = GsonHelper.getAsInt(json, "priority", 0);
        Condition condition = condition(json, "condition", "");
        boolean claim = GsonHelper.getAsBoolean(json, "claim", true);
        Progress progress = null;
        if (json.has("progress")) {
            JsonObject p = GsonHelper.getAsJsonObject(json, "progress");
            only(p, Set.of("value", "direction", "by"), "progress.");
            Value value = Values.parse(p.get("value"));
            if (value == null) throw bad("progress.value", "not a valid Pheno value");
            String direction = GsonHelper.getAsString(p, "direction", "up");
            if (!direction.equals("up") && !direction.equals("down")) throw bad("progress.direction", "up or down");
            double by = GsonHelper.getAsDouble(p, "by", 0.02);
            if (by < 0) throw bad("progress.by", "must be >= 0");
            progress = new Progress(value, direction.equals("down"), by);
        }
        Action start = action(json, "start", "");
        if (!json.has("steps") || !json.get("steps").isJsonArray() || json.getAsJsonArray("steps").isEmpty()) {
            throw bad("steps", "needs at least one step");
        }
        List<Step> steps = new ArrayList<>();
        int i = 0;
        for (JsonElement element : json.getAsJsonArray("steps")) {
            steps.add(step(element.getAsJsonObject(), "steps[" + i++ + "]."));
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (int s = 0; s < steps.size(); s++) {
            for (Option option : steps.get(s).options()) {
                if (option.named() != null && !names.contains(option.named())) {
                    throw bad("steps[" + s + "].find.named", "no earlier step is named '" + option.named() + "'");
                }
            }
            if (steps.get(s).name() != null) names.add(steps.get(s).name());
        }
        Finish finish = null;
        if (json.has("finish")) {
            JsonObject f = GsonHelper.getAsJsonObject(json, "finish");
            only(f, Set.of("min_progress", "action"), "finish.");
            finish = new Finish(GsonHelper.getAsDouble(f, "min_progress", 0), action(f, "action", "finish."));
        }
        int maxTicks = GsonHelper.getAsInt(json, "max_ticks", 2400);
        if (maxTicks < 20) throw bad("max_ticks", "must be at least 20");
        Action end = action(json, "end", "");
        return new RoutineDef(id, priority, condition, claim, progress, start, List.copyOf(steps), finish, maxTicks, end);
    }

    private static Step step(JsonObject json, String at) {
        only(json, STEP_FIELDS, at);
        Stay shared = stay(json, at, null);
        Action onArrive = action(json, "on_arrive", at);
        List<Option> options = new ArrayList<>();
        if (json.has("options")) {
            int i = 0;
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "options")) {
                JsonObject o = element.getAsJsonObject();
                only(o, OPTION_FIELDS, at + "options[" + i + "].");
                options.add(option(o, at + "options[" + i++ + "].", shared));
            }
        } else if (json.has("find")) {
            options.add(option(json, at, null));
        }
        if (options.isEmpty()) throw bad(at + "options", "a step needs a place: options, or find");
        Else orElse = Else.STOP;
        Action elseAction = null;
        if (json.has("else")) {
            JsonElement e = json.get("else");
            if (e.isJsonPrimitive()) {
                orElse = switch (e.getAsString()) {
                    case "stop" -> Else.STOP;
                    case "skip" -> Else.SKIP;
                    default -> throw bad(at + "else", "stop, skip, or a list of actions");
                };
            } else {
                orElse = Else.ACTIONS;
                elseAction = Actions.parse(e);
                if (elseAction == null) throw bad(at + "else", "not valid Pheno actions");
            }
        }
        String name = json.has("name") ? GsonHelper.getAsString(json, "name").trim() : null;
        if (name != null && name.isEmpty()) throw bad(at + "name", "must not be empty");
        return new Step(condition(json, "when", at), List.copyOf(options), json.has("options") ? onArrive : null, orElse, elseAction,
                GsonHelper.getAsBoolean(json, "repeat", false), name);
    }

    /** One place. Inline in a step, the step's own fields are the option's; in {@code options}, unset stay fields come from the step. */
    private static Option option(JsonObject json, String at, @Nullable Stay shared) {
        JsonObject find = GsonHelper.getAsJsonObject(json, "find", null);
        if (find == null || (!find.has("block") && !find.has("named"))) {
            throw bad(at + "find", "needs a block condition, {\"block\": {...}}, or an earlier step's place, {\"named\": \"...\"}");
        }
        BlockCondition block = null;
        String named = null;
        Seeks.Within within = null;
        if (find.has("named")) {
            named = GsonHelper.getAsString(find, "named").trim();
            if (named.isEmpty()) throw bad(at + "find.named", "must not be empty");
        } else {
            block = BlockConditions.parse(find.get("block"));
            if (block == null) throw bad(at + "find.block", "not a valid block condition");
            JsonObject withinJson = json.has("within") ? GsonHelper.getAsJsonObject(json, "within") : radius(16);
            within = SeekActionType.within(withinJson);
            if (within == null) throw bad(at + "within", "unknown place");
        }
        double reach = GsonHelper.getAsDouble(json, "reach", 2);
        String arrive = GsonHelper.getAsString(json, "arrive", "near");
        if (!arrive.equals("near") && !arrive.equals("in_fluid")) throw bad(at + "arrive", "near or in_fluid");
        Value penalty = null;
        if (json.has("penalty")) {
            penalty = Values.parse(json.get("penalty"));
            if (penalty == null) throw bad(at + "penalty", "not a valid Pheno value");
        }
        return new Option(GsonHelper.getAsDouble(json, "worth", 10), block, named, within, reach, arrive.equals("in_fluid"),
                condition(json, "condition", at), penalty, action(json, "on_arrive", at), stay(json, at, shared));
    }

    private static Stay stay(JsonObject json, String at, @Nullable Stay shared) {
        int every = json.has("every") ? GsonHelper.getAsInt(json, "every") : shared != null ? shared.every() : 20;
        int times = json.has("times") ? GsonHelper.getAsInt(json, "times") : shared != null ? shared.times() : 0;
        if (every < 1) throw bad(at + "every", "must be at least 1");
        if (times < 0) throw bad(at + "times", "must be >= 0");
        Action action = json.has("action") ? action(json, "action", at) : shared != null ? shared.action() : null;
        Condition until = json.has("until") ? condition(json, "until", at) : shared != null ? shared.until() : null;
        int stalled = json.has("stalled") ? GsonHelper.getAsInt(json, "stalled") : shared != null ? shared.stalled() : 0;
        if (stalled < 0) throw bad(at + "stalled", "must be >= 0");
        return new Stay(every, times, action, until, stalled);
    }

    private static JsonObject radius(int radius) {
        JsonObject json = new JsonObject();
        json.addProperty("radius", radius);
        return json;
    }

    private static @Nullable Condition condition(JsonObject json, String field, String at) {
        if (!json.has(field)) return null;
        Condition condition = Conditions.parse(json.get(field));
        if (condition == null) throw bad(at + field, "not a valid Pheno condition");
        return condition;
    }

    private static @Nullable Action action(JsonObject json, String field, String at) {
        if (!json.has(field)) return null;
        Action action = Actions.parse(json.get(field));
        if (action == null) throw bad(at + field, "not valid Pheno actions");
        return action;
    }

    private static void only(JsonObject json, Set<String> allowed, String at) {
        for (String field : json.keySet()) if (!allowed.contains(field)) throw bad(at + field, "unknown field");
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> out = new java.util.HashSet<>(a);
        out.addAll(b);
        return Set.copyOf(out);
    }

    private static IllegalArgumentException bad(String field, String message) {
        return new IllegalArgumentException(field + ": " + message);
    }
}
