package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.data.ModGate;
import com.aetherianartificer.townstead.data.TownsteadSchema;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.*;

/** Registry for every generative conversation family. Swapped whole on reload. */
public final class GenerativeDialogue {
    public static final ResourceLocation COMMON_VOICE = ResourceLocation.tryParse("townstead:common");
    public static final String FRAME_SCHEMA = "townstead:dialogue_frame/v1";
    private static final Logger LOGGER = LoggerFactory.getLogger(Townstead.MOD_ID + "/Conversation");

    /** Everything loaded from one reload, with the indexes the composer reads. */
    public record Data(Map<ResourceLocation, ConversationMove> moves, Map<ResourceLocation, SubjectDefinition> subjects,
                       Map<ResourceLocation, Set<ResourceLocation>> subjectTags,
                       Map<ResourceLocation, ConversationEncounter> encounters,
                       Map<ResourceLocation, List<DialogueFrame>> framesByMove,
                       Map<ResourceLocation, List<DialoguePart>> partsByPool,
                       Map<String, List<DialoguePart>> answersByAspect,
                       Map<ResourceLocation, ConversationPair> pairs,
                       Map<ResourceLocation, List<DialoguePart>> responsesByPair, Set<ResourceLocation> respondingPools,
                       Map<ResourceLocation, DialogueVoice> voices, Map<String, BuildingTalk> buildingTalk,
                       List<String> diagnostics) {
        public static final Data EMPTY = new Data(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Set.of(), Map.of(), Map.of(), List.of());

        public boolean ready() {
            return !framesByMove.isEmpty() && !partsByPool.isEmpty();
        }

        public @Nullable ConversationEncounter encounter(ConversationEncounter.Context context) {
            for (ConversationEncounter e : encounters.values()) if (e.context() == context) return e;
            return null;
        }

        /** The voice chain for a culture: its own voice, then every voice it extends, ending in common. */
        public List<ResourceLocation> voiceChain(@Nullable String culture) {
            ResourceLocation start = COMMON_VOICE;
            if (culture != null && !culture.isEmpty()) {
                for (DialogueVoice voice : voices.values()) if (voice.cultures().contains(culture)) { start = voice.id(); break; }
            }
            List<ResourceLocation> chain = new ArrayList<>();
            for (ResourceLocation at = start; at != null && !chain.contains(at); ) {
                chain.add(at);
                DialogueVoice voice = voices.get(at);
                at = voice == null ? (at.equals(COMMON_VOICE) ? null : COMMON_VOICE) : voice.extendsVoice();
            }
            if (!chain.contains(COMMON_VOICE)) chain.add(COMMON_VOICE);
            return List.copyOf(chain);
        }

        /** Whether a subject id matches a filter entry: an id, or a {@code #tag}. */
        public boolean subjectMatches(ResourceLocation subject, String filter) {
            if (filter.startsWith("#")) {
                ResourceLocation tag = ResourceLocation.tryParse(filter.substring(1));
                return tag != null && subjectTags.getOrDefault(tag, Set.of()).contains(subject);
            }
            return subject.toString().equals(filter);
        }
    }

    private static volatile Data data = Data.EMPTY;

    private GenerativeDialogue() {}

    public static Data data() { return data; }

    /** Test hook and command hook: installs a registry built outside a reload. */
    public static void install(Data next) { data = next; }

    public static final List<String> FAMILIES = List.of("conversation_move", "conversation_pair", "conversation_subject", "conversation_encounter",
            "dialogue_frame", "dialogue_pool", "dialogue_line", "dialogue_voice", "extended_buildings", "tags/conversation_subject");

    public static final class Loader extends SimplePreparableReloadListener<Map<String, Map<ResourceLocation, JsonObject>>> {
        @Override
        protected Map<String, Map<ResourceLocation, JsonObject>> prepare(ResourceManager manager, ProfilerFiller profiler) {
            Map<String, Map<ResourceLocation, JsonObject>> loaded = new LinkedHashMap<>();
            for (String family : FAMILIES) {
                Map<ResourceLocation, JsonObject> documents = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
                for (Map.Entry<ResourceLocation, Resource> entry : manager
                        .listResources(family, path -> path.getPath().endsWith(".json")).entrySet()) {
                    ResourceLocation file = entry.getKey();
                    String path = file.getPath();
                    ResourceLocation id = ResourceLocation.tryParse(file.getNamespace() + ":"
                            + path.substring(family.length() + 1, path.length() - 5));
                    if (id == null) continue;
                    try (Reader reader = entry.getValue().openAsReader()) {
                        JsonElement element = JsonParser.parseReader(reader);
                        if (element.isJsonObject()) documents.put(id, element.getAsJsonObject());
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to read {}: {}", file, exception.getMessage());
                    }
                }
                loaded.put(family, documents);
            }
            return loaded;
        }

        @Override
        protected void apply(Map<String, Map<ResourceLocation, JsonObject>> prepared, ResourceManager manager, ProfilerFiller profiler) {
            DataPackLang.loadLangIndex(manager);
            Data next = build(prepared);
            data = next;
            com.aetherianartificer.townstead.dialogue.conversation.ConversationEngine.clear();
            next.diagnostics().forEach(message -> LOGGER.warn("Conversation data: {}", message));
            LOGGER.info("Loaded {} moves, {} subjects, {} encounters, {} frames, {} parts in {} pools, {} voices ({} diagnostics)",
                    next.moves().size(), next.subjects().size(), next.encounters().size(),
                    next.framesByMove().values().stream().mapToInt(List::size).sum(),
                    next.partsByPool().values().stream().mapToInt(List::size).sum(), next.partsByPool().size(),
                    next.voices().size(), next.diagnostics().size());
        }
    }

    /** Parses and cross-checks one set of documents. Public so tests can build a registry from files. */
    public static Data build(Map<String, Map<ResourceLocation, JsonObject>> docs) {
        List<String> errors = new ArrayList<>();
        Map<ResourceLocation, ConversationMove> moves = new LinkedHashMap<>();
        each(docs, "conversation_move", ConversationMove.SCHEMA, errors, (id, json) ->
                several(id, json, "moves", errors, (moveId, def) -> moves.put(moveId, ConversationMove.parse(moveId, def))));
        Map<ResourceLocation, ConversationPair> pairs = new LinkedHashMap<>();
        each(docs, "conversation_pair", ConversationPair.SCHEMA, errors, (id, json) ->
                several(id, json, "pairs", errors, (pairId, def) -> pairs.put(pairId, ConversationPair.parse(pairId, def))));
        Map<ResourceLocation, SubjectDefinition> subjects = new LinkedHashMap<>();
        each(docs, "conversation_subject", SubjectDefinition.SCHEMA, errors, (id, json) -> subjects.put(id, SubjectDefinition.parse(id, json)));
        Map<ResourceLocation, ConversationEncounter> encounters = new LinkedHashMap<>();
        each(docs, "conversation_encounter", ConversationEncounter.SCHEMA, errors,
                (id, json) -> encounters.put(id, ConversationEncounter.parse(id, json)));
        Map<ResourceLocation, List<DialogueFrame>> frames = new LinkedHashMap<>();
        each(docs, "dialogue_frame", FRAME_SCHEMA, errors, (id, json) -> {
            for (DialogueFrame frame : DialogueFrame.parseFile(json)) frames.computeIfAbsent(frame.move(), k -> new ArrayList<>()).add(frame);
        });
        Map<ResourceLocation, List<DialoguePart>> parts = new LinkedHashMap<>();
        Map<String, List<DialoguePart>> answers = new LinkedHashMap<>();
        Map<ResourceLocation, List<DialoguePart>> responses = new LinkedHashMap<>();
        Set<ResourceLocation> responding = new LinkedHashSet<>();
        Map<ResourceLocation, DialogueLines.Pool> pools = new LinkedHashMap<>();
        each(docs, "dialogue_pool", DialogueLines.POOL_SCHEMA, errors, (id, json) -> pools.put(id, DialogueLines.parsePool(id, json)));
        List<DialoguePart> loaded = new ArrayList<>();
        for (DialogueLines.Pool pool : pools.values()) {
            try { DialogueLines.emptyPart(pool).ifPresent(loaded::add); }
            catch (RuntimeException ex) { errors.add("dialogue_pool " + pool.id() + " rejected: " + ex.getMessage()); }
        }
        each(docs, "dialogue_line", DialogueLines.LINE_SCHEMA, errors, (id, json) -> loaded.addAll(DialogueLines.parseFile(json, pools)));
        Set<String> keys = new HashSet<>();
        for (DialoguePart part : loaded) {
            if (!part.empty() && !keys.add(part.key())) { errors.add("duplicate line id " + part.key()); continue; }
            if (!part.empty() && !endsSentence(part.english())) errors.add("line " + part.key() + " does not end a sentence");
            parts.computeIfAbsent(part.pool(), k -> new ArrayList<>()).add(part);
            if (part.answers() != null && !part.empty()) answers.computeIfAbsent(part.answers(), k -> new ArrayList<>()).add(part);
            if (!part.responds().isEmpty()) responding.add(part.pool());
            if (!part.empty()) for (ResourceLocation pair : part.responds()) responses.computeIfAbsent(pair, k -> new ArrayList<>()).add(part);
        }
        Map<ResourceLocation, DialogueVoice> voices = new LinkedHashMap<>();
        each(docs, "dialogue_voice", DialogueVoice.SCHEMA, errors, (id, json) -> voices.put(id, DialogueVoice.parse(id, json)));
        Map<String, BuildingTalk> talk = new LinkedHashMap<>();
        for (var e : docs.getOrDefault("extended_buildings", Map.of()).entrySet()) {
            if (!e.getValue().has("talk")) continue;
            try {
                talk.put(e.getKey().getPath(), BuildingTalk.parse(e.getKey().getPath(), e.getValue().getAsJsonObject("talk")));
            } catch (RuntimeException ex) {
                errors.add("extended_buildings " + e.getKey() + " talk: " + ex.getMessage());
            }
        }
        Map<ResourceLocation, Set<ResourceLocation>> tags = new LinkedHashMap<>();
        for (var e : docs.getOrDefault("tags/conversation_subject", Map.of()).entrySet()) {
            Set<ResourceLocation> members = tags.computeIfAbsent(e.getKey(), k -> new LinkedHashSet<>());
            for (String raw : Json.strings(e.getValue(), "values")) {
                ResourceLocation member = ResourceLocation.tryParse(raw);
                if (member != null) members.add(member);
            }
        }
        crossCheck(moves, frames, parts, answers, voices, encounters, errors);
        Set<ResourceLocation> named = new TreeSet<>(Comparator.comparing(ResourceLocation::toString));
        parts.values().forEach(list -> list.forEach(p -> { if (p.opens() != null) named.add(p.opens()); named.addAll(p.responds()); }));
        for (ResourceLocation pair : named) if (!pairs.containsKey(pair)) errors.add("parts name an unknown pair " + pair);
        for (ConversationPair pair : pairs.values()) if (responses.getOrDefault(pair.id(), List.of()).stream()
                .noneMatch(p -> p.voice().equals(COMMON_VOICE))) errors.add("nothing in the common voice responds to the pair " + pair.id());
        Map<ResourceLocation, List<DialoguePart>> frozenResponses = new LinkedHashMap<>();
        responses.forEach((k, v) -> frozenResponses.put(k, List.copyOf(v)));
        Map<ResourceLocation, List<DialogueFrame>> frozenFrames = new LinkedHashMap<>();
        frames.forEach((k, v) -> frozenFrames.put(k, List.copyOf(v)));
        Map<ResourceLocation, List<DialoguePart>> frozenParts = new LinkedHashMap<>();
        parts.forEach((k, v) -> frozenParts.put(k, List.copyOf(v)));
        Map<String, List<DialoguePart>> frozenAnswers = new LinkedHashMap<>();
        answers.forEach((k, v) -> frozenAnswers.put(k, List.copyOf(v)));
        Map<ResourceLocation, Set<ResourceLocation>> frozenTags = new LinkedHashMap<>();
        tags.forEach((k, v) -> frozenTags.put(k, Set.copyOf(v)));
        return new Data(Map.copyOf(moves), Map.copyOf(subjects), Map.copyOf(frozenTags), Map.copyOf(encounters),
                Map.copyOf(frozenFrames), Map.copyOf(frozenParts), Map.copyOf(frozenAnswers), Map.copyOf(pairs),
                Map.copyOf(frozenResponses), Set.copyOf(responding), Map.copyOf(voices),
                Map.copyOf(talk), List.copyOf(errors));
    }

    private static void crossCheck(Map<ResourceLocation, ConversationMove> moves, Map<ResourceLocation, List<DialogueFrame>> frames,
                                   Map<ResourceLocation, List<DialoguePart>> parts, Map<String, List<DialoguePart>> answers,
                                   Map<ResourceLocation, DialogueVoice> voices, Map<ResourceLocation, ConversationEncounter> encounters,
                                   List<String> errors) {
        for (var e : frames.entrySet()) {
            if (!moves.containsKey(e.getKey())) errors.add("frames name an unknown move " + e.getKey());
            for (DialogueFrame frame : e.getValue()) {
                for (int i = 0; i < frame.slots().size(); i++) {
                    DialogueFrame.Slot slot = frame.slots().get(i);
                    if (slot.pooled() && !parts.containsKey(slot.pool())) errors.add("frame for " + e.getKey() + " uses an empty pool " + slot.pool());
                    if (slot.pooled() && i < frame.slots().size() - 1 && parts.getOrDefault(slot.pool(), List.of()).stream()
                            .anyMatch(p -> p.asks() != null)) errors.add("frame for " + e.getKey() + ": pool " + slot.pool()
                            + " asks questions but is not the last slot");
                }
            }
        }
        Set<String> asked = new TreeSet<>();
        parts.values().forEach(list -> list.forEach(p -> { if (p.asks() != null) asked.add(p.asks()); }));
        for (String aspect : asked) if (!answers.containsKey(aspect)) errors.add("nothing answers the question " + aspect);
        for (DialogueVoice voice : voices.values()) {
            Set<ResourceLocation> seen = new HashSet<>();
            for (ResourceLocation at = voice.id(); at != null; at = voices.containsKey(at) ? voices.get(at).extendsVoice() : null) {
                if (!seen.add(at)) { errors.add("voice " + voice.id() + " extends in a cycle"); break; }
            }
        }
        for (ConversationEncounter encounter : encounters.values()) {
            for (var list : List.of(encounter.opening(), encounter.openingAgain(), encounter.closing()))
                for (var sequence : list) for (ResourceLocation move : sequence.moves())
                    if (!moves.containsKey(move)) errors.add("encounter " + encounter.id() + " uses an unknown move " + move);
        }
    }

    @FunctionalInterface private interface Handler { void accept(ResourceLocation id, JsonObject json); }

    /** A file holds one definition, or a map of them under {@code field}; map ids take the file's namespace. */
    private static void several(ResourceLocation file, JsonObject json, String field, List<String> errors, Handler handler) {
        if (!json.has(field)) { handler.accept(file, json); return; }
        for (var e : json.getAsJsonObject(field).entrySet()) {
            ResourceLocation id = ResourceLocation.tryParse(e.getKey().indexOf(':') < 0 ? file.getNamespace() + ":" + e.getKey() : e.getKey());
            try {
                handler.accept(id, e.getValue().getAsJsonObject());
            } catch (RuntimeException ex) {
                errors.add(file + " " + field + "." + e.getKey() + " rejected: " + ex.getMessage());
            }
        }
    }

    private static boolean endsSentence(String text) {
        String trimmed = text.stripTrailing();
        if (trimmed.isEmpty()) return false;
        char last = trimmed.charAt(trimmed.length() - 1);
        return ".!?\u2026\"')".indexOf(last) >= 0 || last == '\u3002' || last == '\uff01' || last == '\uff1f';
    }

    private static void each(Map<String, Map<ResourceLocation, JsonObject>> docs, String family, String schema,
                             List<String> errors, Handler handler) {
        for (var e : docs.getOrDefault(family, Map.of()).entrySet()) {
            JsonObject json = e.getValue();
            try {
                if (json.has("mods")) {
                    Boolean enabled = ModGate.evaluate(json.get("mods"));
                    if (enabled == null) throw new IllegalArgumentException("malformed mods gate");
                    if (!enabled) continue;
                }
                TownsteadSchema.validateRequired(json, schema);
                handler.accept(e.getKey(), json);
            } catch (RuntimeException ex) {
                errors.add(family + " " + e.getKey() + " rejected: " + ex.getMessage());
            }
        }
    }
}
