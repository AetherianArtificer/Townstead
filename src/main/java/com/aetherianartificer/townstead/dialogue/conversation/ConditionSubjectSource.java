package com.aetherianartificer.townstead.dialogue.conversation;

import com.aetherianartificer.townstead.dialogue.conversation.generative.LineComposer;
import com.aetherianartificer.townstead.pheno.condition.Condition;
import com.aetherianartificer.townstead.pheno.condition.ConditionContext;
import com.aetherianartificer.townstead.pheno.condition.Conditions;
import com.aetherianartificer.townstead.pheno.selector.SelectorContext;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.Values;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The {@code townstead:condition} subject source: a topic that comes up when a Pheno condition holds for the speaker or
 * the one they talk to, so a mod offers a subject with data alone.
 *
 * <pre>
 * "source": {
 *   "type": "townstead:condition",
 *   "speaker":  { "type": "examplepack:body_dirt", "min": 0.25 },
 *   "listener": { "type": "examplepack:body_dirt", "min": 0.25 },
 *   "rank": { "type": "examplepack:body_dirt" },
 *   "urgency": [ { "when": { "type": "examplepack:body_dirt", "min": 0.6 }, "value": 0.7 }, { "value": 0.4 } ],
 *   "valence": "negative"
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code speaker}, {@code listener}: when the speaker's condition holds the subject's variant is {@code self}; when the
 *       listener's holds it is {@code you}. Either may be left out. Each is tested on that person, with the other as
 *       the other entity.</li>
 *   <li>{@code rank}: a value; when both hold, the person it ranks higher is the subject (a tie goes to the speaker).
 *       Without it the speaker comes first.</li>
 *   <li>{@code urgency}: rules tested on the person the subject is about; the first that holds gives the urgency.
 *       Without a match, or without rules, the urgency is the default.</li>
 *   <li>{@code valence}: {@code positive}, {@code negative} or {@code neutral} (the default).</li>
 * </ul>
 */
final class ConditionSubjectSource {

    private static final Logger LOGGER = LoggerFactory.getLogger("townstead/ConditionSubjectSource");
    private static final Set<String> VALENCES = Set.of("positive", "negative", "neutral");

    record Rule(@Nullable Condition when, double value) {}

    record Parsed(JsonObject source, @Nullable Condition speaker, @Nullable Condition listener, @Nullable Value rank,
                          List<Rule> urgency, String valence) {}

    /** Parsed sources, by subject; a reload hands over new JSON, which is parsed again. */
    private static final Map<ResourceLocation, Parsed> PARSED = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> REPORTED = ConcurrentHashMap.newKeySet();

    private ConditionSubjectSource() {}

    static List<LineComposer.Subject> instances(SubjectSources.Query q) {
        Parsed parsed = parsed(q.subject().id(), q.subject().source());
        if (parsed == null) return List.of();
        boolean self = parsed.speaker() != null && holds(parsed.speaker(), q.speaker(), q.listener());
        boolean you = parsed.listener() != null && holds(parsed.listener(), q.listener(), q.speaker());
        if (!self && !you) return List.of();
        if (self && you && parsed.rank() != null) self = rank(parsed.rank(), q.speaker(), q.listener()) >= rank(parsed.rank(), q.listener(), q.speaker());
        LivingEntity about = self ? q.speaker() : q.listener();
        LivingEntity other = self ? q.listener() : q.speaker();
        String variant = self ? "self" : "you";
        for (Rule rule : parsed.urgency()) {
            if (rule.when() == null || holds(rule.when(), about, other)) {
                return List.of(new LineComposer.Subject(q.subject().id(), variant, parsed.valence(), Map.of(), Set.of(), null, rule.value()));
            }
        }
        return List.of(new LineComposer.Subject(q.subject().id(), variant, parsed.valence(), Map.of(), Set.of(), null));
    }

    private static boolean holds(Condition condition, LivingEntity who, LivingEntity other) {
        return condition.test(new ConditionContext(who, other));
    }

    private static double rank(Value value, LivingEntity who, LivingEntity other) {
        return value.get(SelectorContext.of(new ConditionContext(who, other)));
    }

    private static @Nullable Parsed parsed(ResourceLocation id, JsonObject source) {
        Parsed kept = PARSED.get(id);
        if (kept != null && kept.source() == source) return kept;
        try {
            Parsed fresh = parse(source);
            PARSED.put(id, fresh);
            REPORTED.remove(id);
            return fresh;
        } catch (RuntimeException e) {
            if (REPORTED.add(id)) LOGGER.warn("Conversation subject {} has a bad townstead:condition source: {}", id, e.getMessage());
            return null;
        }
    }

    static Parsed parse(JsonObject source) {
        for (String field : source.keySet()) {
            if (!Set.of("type", "speaker", "listener", "rank", "urgency", "valence").contains(field)) {
                throw new IllegalArgumentException(field + ": unknown field");
            }
        }
        Condition speaker = condition(source, "speaker");
        Condition listener = condition(source, "listener");
        if (speaker == null && listener == null) throw new IllegalArgumentException("needs a speaker or a listener condition");
        Value rank = null;
        if (source.has("rank")) {
            rank = Values.parse(source.get("rank"));
            if (rank == null) throw new IllegalArgumentException("rank: not a valid Pheno value");
        }
        List<Rule> urgency = new ArrayList<>();
        if (source.has("urgency")) {
            for (JsonElement element : GsonHelper.getAsJsonArray(source, "urgency")) {
                JsonObject rule = element.getAsJsonObject();
                Condition when = condition(rule, "when");
                double value = GsonHelper.getAsDouble(rule, "value");
                if (value < 0 || value > 1) throw new IllegalArgumentException("urgency.value: must be between 0 and 1");
                urgency.add(new Rule(when, value));
            }
        }
        String valence = GsonHelper.getAsString(source, "valence", "neutral");
        if (!VALENCES.contains(valence)) throw new IllegalArgumentException("valence: unknown " + valence);
        return new Parsed(source, speaker, listener, rank, List.copyOf(urgency), valence);
    }

    private static @Nullable Condition condition(JsonObject json, String field) {
        if (!json.has(field)) return null;
        Condition condition = Conditions.parse(json.get(field));
        if (condition == null) throw new IllegalArgumentException(field + ": not a valid Pheno condition");
        return condition;
    }
}
