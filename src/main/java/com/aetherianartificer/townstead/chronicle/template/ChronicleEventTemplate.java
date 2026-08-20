package com.aetherianartificer.townstead.chronicle.template;

import com.aetherianartificer.townstead.chronicle.model.ChronicleEvent;
import com.aetherianartificer.townstead.chronicle.model.ChronicleRef;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.action.Action;
import com.aetherianartificer.townstead.pheno.action.Actions;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A data-pack chronicle event definition ({@code data/<ns>/chronicle_event/}).
 *
 * <p>Boundary rules: role eligibility uses pheno <em>conditions</em>; the
 * {@code impact} block (mood/sentiment/memory) is structured data interpreted
 * by the chronicle engine, never pheno actions; the optional {@code effects}
 * block is plain pheno actions for generic world side effects only.</p>
 */
public record ChronicleEventTemplate(
        ResourceLocation id,
        String category,
        int weight,
        Rarity rarity,
        boolean keep,
        Set<Context> contexts,
        @Nullable TriggerKey trigger,
        int cooldownDays,
        int reach,
        float newsValue,
        List<RoleSpec> roles,
        WitnessSpec witnesses,
        DisplaySpec display,
        Map<String, Impact> impacts,
        DistortionSpec distortion,
        Map<String, Action> effects,
        List<CounterGrant> counters,
        Map<String, PregenParamSource> pregenParams,
        int maxPerLife,
        @Nullable PregenBond pregenBond,
        float magnitudeMin,
        float magnitudeMax) {

    public ChronicleEventTemplate {
        pregenParams = pregenParams == null ? Map.of() : pregenParams;
    }

    public enum Context { LIVE, PREGEN, EXPEDITION }

    /**
     * How often a template is picked, and only that. Importance is
     * {@code news_value} alone, so making something rarer does not quietly make
     * it a bigger deal.
     */
    public enum Rarity {
        COMMON(1.0f), UNCOMMON(0.6f), RARE(0.25f), LEGENDARY(0.05f);

        public final float weightMultiplier;

        Rarity(float weightMultiplier) {
            this.weightMultiplier = weightMultiplier;
        }
    }

    /** Semantic tap key, e.g. {@code work / townstead:cooked}. */
    public record TriggerKey(String type, String key) {
        @Override
        public String toString() {
            return type + "/" + key;
        }
    }

    /**
     * A named role. {@code when} is a pheno condition and gates both live
     * emission and pre-history binding: pre-history evaluates it against a
     * {@link com.aetherianartificer.townstead.pheno.condition.PhenoSubject}, so
     * a gate only works there if every part of it reports
     * {@link Condition#supportsSubject()}.
     *
     * <p>{@code fatal} marks a role the participant does not walk away from, so
     * a living subject's own history never binds it. That is a property of the
     * role rather than a test, which is why it is not a condition.</p>
     */
    public record RoleSpec(String id, ChronicleRef.Kind kind, @Nullable Condition when,
                           boolean fatal, @Nullable String fromBond,
                           @Nullable String involvement) {}

    /**
     * A tie a fabricated event forms or ends: {@code kind} with whoever holds
     * {@code withRole}. {@code ends} marks the second case — a death, a parting —
     * which is what lets a life hold "was married once, is not now" rather than
     * only "married" and "never married".
     */
    public record PregenBond(String kind, String withRole, boolean ends) {}

    /**
     * Where pre-history sources a display param that live emission takes from a
     * real object ({@code ChronicleTaps.work} passes the item name). A tag, not
     * a list of ids: packs decide what a feast can consist of, and the text
     * comes from the item, so nothing needs translating here.
     */
    public record PregenParamSource(ResourceLocation itemTag) {}

    public record WitnessSpec(double radius, int max) {
        public static final WitnessSpec NONE = new WitnessSpec(0.0, 0);
    }

    /** Server-resolved display: literal fallback + lang key + ordered param names. */
    public record DisplaySpec(String headlineLiteral, String headlineLangKey, List<String> paramNames) {
        public DisplaySpec {
            paramNames = paramNames == null ? List.of() : List.copyOf(paramNames);
        }
    }

    /** Chronicle-engine impact data. Keys: role ids, "witness", or "on_learn". */
    public record Impact(float mood, @Nullable SentimentImpact sentiment, @Nullable MemoryImpact memory) {}

    public record SentimentImpact(String towardRole, float delta) {}

    public record MemoryImpact(float valence, float strength) {}

    /** How hearsay may mutate. {@code mutatesTo} is schema-only until the info arc. */
    public record DistortionSpec(float magnitudeDrift, float valenceDrift,
                                 Set<String> substitutableRoles, List<ResourceLocation> mutatesTo) {
        public static final DistortionSpec DEFAULT =
                new DistortionSpec(1.1f, 0.15f, Set.of(), List.of());
    }

    public record CounterGrant(String role, String key, int amount) {}

    public RoleSpec primaryRole() {
        return roles.get(0);
    }

    public float pickWeight() {
        return weight * rarity.weightMultiplier;
    }

    /** The server-resolved headline with this event's params filled in. */
    public String headline(Map<String, String> params) {
        List<String> names = display.paramNames();
        if (names.isEmpty()) return display.headlineLiteral();
        Object[] values = new Object[names.size()];
        for (int i = 0; i < names.size(); i++) {
            values[i] = params.getOrDefault(names.get(i), "");
        }
        try {
            return String.format(Locale.ROOT, display.headlineLiteral(), values);
        } catch (Exception ex) {
            return display.headlineLiteral();
        }
    }

    /**
     * Display params pre-history cannot fill, because no role carries them.
     * Live emission fills these from the tap ({@code ChronicleTaps.work} passes
     * the item name), so an entry here only misrenders in a fabricated past.
     */
    public List<String> unfillablePregenParams() {
        if (!contexts.contains(Context.PREGEN)) return List.of();
        List<String> unfillable = new ArrayList<>();
        for (String param : display.paramNames()) {
            if (pregenParams.containsKey(param)) continue;
            boolean isRole = false;
            for (RoleSpec role : roles) {
                if (role.id().equals(param)) {
                    isRole = true;
                    break;
                }
            }
            if (!isRole) unfillable.add(param);
        }
        return unfillable;
    }

    // ---- parsing ----

    public static ChronicleEventTemplate parse(ResourceLocation id, JsonObject json,
                                               Map<String, String> lang) {
        String category = GsonHelper.getAsString(json, "category", "misc");
        int weight = GsonHelper.getAsInt(json, "weight", 10);
        Rarity rarity = parseEnum(GsonHelper.getAsString(json, "rarity", "common"), Rarity.class, Rarity.COMMON);
        boolean keep = GsonHelper.getAsBoolean(json, "keep", rarity.ordinal() >= Rarity.RARE.ordinal());

        Set<Context> contexts = EnumSet.noneOf(Context.class);
        if (json.has("contexts")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "contexts")) {
                contexts.add(parseEnum(e.getAsString(), Context.class, Context.LIVE));
            }
        } else {
            contexts.add(Context.LIVE);
        }

        TriggerKey trigger = null;
        if (json.has("trigger")) {
            JsonObject t = GsonHelper.getAsJsonObject(json, "trigger");
            trigger = new TriggerKey(
                    GsonHelper.getAsString(t, "type", "work"),
                    GsonHelper.getAsString(t, "key", ""));
        }

        int cooldownDays = GsonHelper.getAsInt(json, "cooldown_days", 0);
        int reach = parseReach(GsonHelper.getAsString(json, "reach", "witnesses"));
        float newsValue = GsonHelper.getAsFloat(json, "news_value", 1.0f);

        List<RoleSpec> roles = new ArrayList<>();
        if (json.has("roles")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "roles")) {
                JsonObject r = e.getAsJsonObject();
                String roleId = GsonHelper.getAsString(r, "id");
                ChronicleRef.Kind kind = parseEnum(
                        GsonHelper.getAsString(r, "kind", "villager"), ChronicleRef.Kind.class,
                        ChronicleRef.Kind.VILLAGER);
                Condition when = r.has("when") ? Conditions.parse(r.get("when")) : null;
                boolean fatal = false;
                String fromBond = null;
                if (r.has("pregen")) {
                    JsonObject rolePregen = GsonHelper.getAsJsonObject(r, "pregen");
                    fatal = GsonHelper.getAsBoolean(rolePregen, "fatal", false);
                    // Bind whoever the subject already holds this bond with, so a
                    // story about a spouse is about the spouse they actually have.
                    String bond = GsonHelper.getAsString(rolePregen, "from_bond", "");
                    fromBond = bond.isEmpty() ? null : bond;
                }
                // What this role's part in the event actually is. A role that only
                // saw it happen must not carry a protagonist's weight, or standing
                // near a noise outranks getting married.
                String involvement = r.has("involvement")
                        ? GsonHelper.getAsString(r, "involvement") : null;
                roles.add(new RoleSpec(roleId, kind, when, fatal, fromBond, involvement));
            }
        }
        if (roles.isEmpty()) {
            throw new IllegalArgumentException("chronicle_event needs at least one role");
        }

        WitnessSpec witnesses = WitnessSpec.NONE;
        if (json.has("witnesses")) {
            JsonObject w = GsonHelper.getAsJsonObject(json, "witnesses");
            witnesses = new WitnessSpec(
                    GsonHelper.getAsDouble(w, "radius", 12.0),
                    GsonHelper.getAsInt(w, "max", 6));
        }

        DisplaySpec display = parseDisplay(id, json, lang);
        Map<String, Impact> impacts = parseImpacts(json);
        DistortionSpec distortion = parseDistortion(json);

        Map<String, Action> effects = new HashMap<>();
        if (json.has("effects")) {
            JsonObject e = GsonHelper.getAsJsonObject(json, "effects");
            for (String role : e.keySet()) {
                Action action = Actions.parse(e.get(role));
                if (action != null) effects.put(role, action);
            }
        }

        List<CounterGrant> counters = new ArrayList<>();
        if (json.has("counters")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "counters")) {
                JsonObject c = e.getAsJsonObject();
                counters.add(new CounterGrant(
                        GsonHelper.getAsString(c, "role"),
                        GsonHelper.getAsString(c, "key"),
                        GsonHelper.getAsInt(c, "amount", 1)));
            }
        }

        return new ChronicleEventTemplate(id, category, weight, rarity, keep, contexts, trigger,
                cooldownDays, reach, newsValue, List.copyOf(roles), witnesses, display,
                Map.copyOf(impacts), distortion, Map.copyOf(effects), List.copyOf(counters),
                parsePregenParams(json), parseMaxPerLife(json), parseBond(json),
                magnitudeBound(json, true), magnitudeBound(json, false));
    }

    /**
     * How much this occurrence may vary in a fabricated past: {@code pregen.magnitude}
     * as one number (fixed) or {@code [min, max]}. Defaults to the wide working range.
     */
    private static float magnitudeBound(JsonObject json, boolean min) {
        float fallback = min
                ? com.aetherianartificer.townstead.chronicle.pregen.PregenMagnitude.DEFAULT_MIN
                : com.aetherianartificer.townstead.chronicle.pregen.PregenMagnitude.DEFAULT_MAX;
        if (!json.has("pregen")) return fallback;
        JsonObject pregen = GsonHelper.getAsJsonObject(json, "pregen");
        if (!pregen.has("magnitude")) return fallback;
        JsonElement spec = pregen.get("magnitude");
        if (spec.isJsonArray() && spec.getAsJsonArray().size() == 2) {
            return spec.getAsJsonArray().get(min ? 0 : 1).getAsFloat();
        }
        if (spec.isJsonPrimitive() && spec.getAsJsonPrimitive().isNumber()) {
            return spec.getAsFloat();
        }
        return fallback;
    }

    /** The tie this forms, or ends, when it happens in a fabricated past. */
    private static @Nullable PregenBond parseBond(JsonObject json) {
        if (!json.has("pregen")) return null;
        JsonObject pregen = GsonHelper.getAsJsonObject(json, "pregen");
        boolean ends = pregen.has("ends_bond");
        String block = ends ? "ends_bond" : "bond";
        if (!pregen.has(block)) return null;
        JsonObject bond = GsonHelper.getAsJsonObject(pregen, block);
        String kind = GsonHelper.getAsString(bond, "kind", "");
        String withRole = GsonHelper.getAsString(bond, "with", "");
        return kind.isEmpty() || withRole.isEmpty() ? null : new PregenBond(kind, withRole, ends);
    }

    /**
     * How often one person's fabricated life may contain this, 0 for no limit.
     * Weddings happen once or twice; arguments happen forever.
     */
    private static int parseMaxPerLife(JsonObject json) {
        if (!json.has("pregen")) return 0;
        JsonObject pregen = GsonHelper.getAsJsonObject(json, "pregen");
        return Math.max(0, GsonHelper.getAsInt(pregen, "max_per_life", 0));
    }

    private static Map<String, PregenParamSource> parsePregenParams(JsonObject json) {
        if (!json.has("pregen")) return Map.of();
        JsonObject pregen = GsonHelper.getAsJsonObject(json, "pregen");
        if (!pregen.has("params")) return Map.of();
        JsonObject params = GsonHelper.getAsJsonObject(pregen, "params");
        Map<String, PregenParamSource> sources = new HashMap<>();
        for (String param : params.keySet()) {
            JsonObject spec = GsonHelper.getAsJsonObject(params, param);
            ResourceLocation tag = DataPackLang.parseId(GsonHelper.getAsString(spec, "item_tag", ""));
            if (tag != null) sources.put(param, new PregenParamSource(tag));
        }
        return Map.copyOf(sources);
    }

    private static DisplaySpec parseDisplay(ResourceLocation id, JsonObject json, Map<String, String> lang) {
        String defaultKey = "chronicle." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        String langKey = defaultKey;
        String literal = id.getPath();
        List<String> paramNames = new ArrayList<>();
        if (json.has("display")) {
            JsonObject d = GsonHelper.getAsJsonObject(json, "display");
            if (d.has("headline")) {
                JsonElement h = d.get("headline");
                if (h.isJsonObject() && h.getAsJsonObject().has("translate")) {
                    langKey = h.getAsJsonObject().get("translate").getAsString();
                } else if (h.isJsonPrimitive()) {
                    literal = h.getAsString();
                }
            }
            if (d.has("params")) {
                for (JsonElement p : GsonHelper.getAsJsonArray(d, "params")) paramNames.add(p.getAsString());
            }
        }
        literal = DataPackLang.resolveFallback(langKey, "en_us", literal);
        String indexed = lang.get(langKey);
        if (indexed != null) literal = indexed;
        return new DisplaySpec(literal, langKey, paramNames);
    }

    private static Map<String, Impact> parseImpacts(JsonObject json) {
        Map<String, Impact> impacts = new HashMap<>();
        if (!json.has("impact")) return impacts;
        JsonObject block = GsonHelper.getAsJsonObject(json, "impact");
        for (String key : block.keySet()) {
            JsonObject i = GsonHelper.getAsJsonObject(block, key);
            SentimentImpact sentiment = null;
            if (i.has("sentiment")) {
                JsonObject s = GsonHelper.getAsJsonObject(i, "sentiment");
                sentiment = new SentimentImpact(
                        GsonHelper.getAsString(s, "toward"),
                        GsonHelper.getAsFloat(s, "delta", 0f));
            }
            MemoryImpact memory = null;
            if (i.has("memory")) {
                JsonObject m = GsonHelper.getAsJsonObject(i, "memory");
                memory = new MemoryImpact(
                        GsonHelper.getAsFloat(m, "valence", 0f),
                        GsonHelper.getAsFloat(m, "strength", 0.5f));
            }
            impacts.put(key, new Impact(GsonHelper.getAsFloat(i, "mood", 0f), sentiment, memory));
        }
        return impacts;
    }

    private static DistortionSpec parseDistortion(JsonObject json) {
        if (!json.has("distortion")) return DistortionSpec.DEFAULT;
        JsonObject d = GsonHelper.getAsJsonObject(json, "distortion");
        Set<String> substitutable = new HashSet<>();
        if (d.has("substitutable")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(d, "substitutable")) substitutable.add(e.getAsString());
        }
        List<ResourceLocation> mutatesTo = new ArrayList<>();
        if (d.has("mutates_to")) {
            for (JsonElement e : GsonHelper.getAsJsonArray(d, "mutates_to")) {
                mutatesTo.add(DataPackLang.parseId(e.getAsString()));
            }
        }
        return new DistortionSpec(
                GsonHelper.getAsFloat(d, "magnitude_drift", 1.1f),
                GsonHelper.getAsFloat(d, "valence_drift", 0.15f),
                Set.copyOf(substitutable),
                List.copyOf(mutatesTo));
    }

    private static int parseReach(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "none" -> ChronicleEvent.REACH_NONE;
            case "village" -> ChronicleEvent.REACH_VILLAGE;
            case "world" -> ChronicleEvent.REACH_WORLD;
            default -> ChronicleEvent.REACH_WITNESSES;
        };
    }

    private static <T extends Enum<T>> T parseEnum(String value, Class<T> type, T fallback) {
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
