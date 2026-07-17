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
        List<CounterGrant> counters) {

    public enum Context { LIVE, PREGEN, EXPEDITION }

    public enum Rarity {
        COMMON(1.0f, 1.0f), UNCOMMON(0.6f, 1.5f), RARE(0.25f, 2.5f), LEGENDARY(0.05f, 5.0f);

        public final float weightMultiplier;
        public final float newsMultiplier;

        Rarity(float weightMultiplier, float newsMultiplier) {
            this.weightMultiplier = weightMultiplier;
            this.newsMultiplier = newsMultiplier;
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
     * A named role. {@code when} gates live emission (entity-backed); the
     * declarative {@code pregen} filter binds entity-less pre-history roles.
     */
    public record RoleSpec(String id, ChronicleRef.Kind kind, @Nullable Condition when,
                           @Nullable PregenFilter pregen) {}

    public record PregenFilter(@Nullable String profession, @Nullable String age, @Nullable String gender) {}

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
                PregenFilter pregen = null;
                if (r.has("pregen")) {
                    JsonObject p = GsonHelper.getAsJsonObject(r, "pregen");
                    pregen = new PregenFilter(
                            p.has("profession") ? GsonHelper.getAsString(p, "profession") : null,
                            p.has("age") ? GsonHelper.getAsString(p, "age") : null,
                            p.has("gender") ? GsonHelper.getAsString(p, "gender") : null);
                }
                roles.add(new RoleSpec(roleId, kind, when, pregen));
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
                Map.copyOf(impacts), distortion, Map.copyOf(effects), List.copyOf(counters));
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
