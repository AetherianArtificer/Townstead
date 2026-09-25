package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One piece of a line. A part carries its role in the conversation (act, open question, answer,
 * stance, form, perspective) so the composer only picks parts that fit what was said before.
 * {@code english} is the source text; other locales translate {@code key}.
 */
public record DialoguePart(ResourceLocation pool, ResourceLocation voice, @Nullable String key, @Nullable String english,
                           double weight, Act act, @Nullable String asks, @Nullable String answers,
                           @Nullable ResourceLocation opens, Set<ResourceLocation> responds, @Nullable String about,
                           @Nullable String thread,
                           @Nullable String stance, @Nullable String form, Map<String, String> sets,
                           Map<String, Set<String>> state, Map<String, String> persp, boolean introduces,
                           List<String> args, Set<String> subjects, Set<String> variants, Set<String> registers,
                           Set<String> valence, Set<String> provenance, Set<String> stages, Set<String> personalities,
                           boolean marked, boolean simple, int minLines, List<String> requires, Set<String> contentTags,
                           ConversationTopic.Gate gate) {
    public enum Act { GREET, ASK, ANSWER, INFORM, ASSESS, REACT, FAREWELL, FILLER, RHETORICAL }

    /** Fields a pool, a group or a line can set. */
    static final String[] FIELDS = {"weight", "act", "asks", "answers", "opens", "responds", "about", "thread", "stance", "form", "sets",
            "state", "persp", "introduces", "subjects", "variants", "variant", "registers", "valence", "provenance", "stages",
            "personalities", "marked", "simple", "min_lines", "requires", "content_tags", "context", "relationship",
            "personality_weights", "relationship_weights", "when", "evaluation"};

    /** {@code {name}} or {@code {name.meta:value=text|*=text}} in a text. */
    static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z0-9_]+)(?:\\.[a-z0-9_]+:[^}]*)?}");

    public boolean empty() { return key == null; }

    /** How many conditions narrow this part. A part written for a narrow moment is preferred over a general one. */
    public int specificity() {
        int n = 0;
        if (!variants.isEmpty()) n++;
        if (!stages.isEmpty()) n++;
        if (!personalities.isEmpty()) n++;
        if (!registers.isEmpty()) n++;
        if (!valence.isEmpty()) n++;
        if (!provenance.isEmpty()) n++;
        if (minLines > 0) n++;
        if (!gate.isOpen()) n++;
        return n + state.size() + requires.size() + persp.size();
    }

    /** The root of a thread: {@code rain.crops} has the root {@code rain}. */
    public static String threadRoot(String thread) {
        int dot = thread.indexOf('.');
        return dot < 0 ? thread : thread.substring(0, dot);
    }

    /** A statement changes what an agreement can answer. */
    public boolean statement() { return !empty() && (act == Act.INFORM || act == Act.ASSESS); }

    /** Slot names in the order the text first uses them. */
    static List<String> placeholders(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) out.add(m.group(1));
        return List.copyOf(out);
    }

    /** Builds a part from fields already merged from its pool, groups and line. {@code key} is null for an empty part. */
    static DialoguePart parse(ResourceLocation pool, ResourceLocation voice, @Nullable String key, @Nullable String text,
                              JsonObject json) {
        if (key != null && (text == null || text.isBlank())) throw Json.bad("text", "is required");
        String asks = optional(json, "asks"), answers = optional(json, "answers");
        Act act = json.has("act") ? act(GsonHelper.getAsString(json, "act"))
                : asks != null ? Act.ASK : answers != null ? Act.ANSWER : Act.INFORM;
        String stance = optional(json, "stance");
        if (stance != null && !Set.of("+", "-", "0").contains(stance)) throw Json.bad("stance", "must be +, - or 0");
        Map<String, String> sets = new LinkedHashMap<>();
        if (json.has("sets")) for (var e : GsonHelper.getAsJsonObject(json, "sets").entrySet())
            sets.put(e.getKey(), Json.scalar(e.getValue(), "sets." + e.getKey()));
        Map<String, Set<String>> state = new LinkedHashMap<>();
        if (json.has("state")) for (var e : GsonHelper.getAsJsonObject(json, "state").entrySet()) {
            Set<String> allowed = new LinkedHashSet<>();
            if (e.getValue().isJsonArray()) for (JsonElement v : e.getValue().getAsJsonArray()) allowed.add(Json.scalar(v, "state." + e.getKey()));
            else allowed.add(Json.scalar(e.getValue(), "state." + e.getKey()));
            state.put(e.getKey(), Set.copyOf(allowed));
        }
        Map<String, String> persp = new LinkedHashMap<>();
        if (json.has("persp")) for (var e : GsonHelper.getAsJsonObject(json, "persp").entrySet()) {
            String relation = e.getValue().getAsString();
            if (!Set.of("me", "you", "they").contains(relation)) throw Json.bad("persp." + e.getKey(), "must be me, you or they");
            persp.put(e.getKey(), relation);
        }
        int minLines = GsonHelper.getAsInt(json, "min_lines", 0);
        if (minLines < 0) throw Json.bad("min_lines", "must be >= 0");
        // A question is already a pair of its own, so it takes the place of any pair from the defaults.
        ResourceLocation opens = asks != null ? null : Json.id(json, "opens");
        Set<String> variants = new LinkedHashSet<>(Json.strings(json, "variants"));
        variants.addAll(Json.strings(json, "variant"));
        return new DialoguePart(pool, voice, key, key == null ? null : text, Json.number(json, "weight", 1, 0.0001, 100), act,
                asks, answers, opens, Set.copyOf(Json.ids(json, "responds")), optional(json, "about"), optional(json, "thread"), stance,
                optional(json, "form"), Map.copyOf(sets), Map.copyOf(state), Map.copyOf(persp),
                GsonHelper.getAsBoolean(json, "introduces", false), text == null ? List.of() : placeholders(text),
                Json.subjectSet(json, "subjects"), Set.copyOf(variants), Json.stringSet(json, "registers"),
                Json.stringSet(json, "valence"), Json.stringSet(json, "provenance"), Json.stringSet(json, "stages"),
                Json.stringSet(json, "personalities"), GsonHelper.getAsBoolean(json, "marked", false),
                GsonHelper.getAsBoolean(json, "simple", false), minLines, Json.strings(json, "requires"),
                Json.stringSet(json, "content_tags"), ConversationTopic.gate(json));
    }

    private static Act act(String raw) {
        try { return Act.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw Json.bad("act", "unknown act " + raw); }
    }

    private static @Nullable String optional(JsonObject json, String key) {
        return json.has(key) ? GsonHelper.getAsString(json, key) : null;
    }
}
