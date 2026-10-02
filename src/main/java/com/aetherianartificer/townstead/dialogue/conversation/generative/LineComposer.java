package com.aetherianartificer.townstead.dialogue.conversation.generative;

import com.aetherianartificer.townstead.dialogue.conversation.ConversationTopic;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Builds one line from a frame and parts. Pure logic: the world is reached only through
 * {@link World} and {@link Recency}, so every rule is unit testable.
 *
 * <p>Rules, in the order they apply: frames follow the question state; an open question puts an
 * answer first; an open pair puts a fitting reply first, either by narrowing a reply pool that
 * already starts the line or by adding a reply before it; parts are picked left to right against a scratch copy of the state; a question can
 * only end a line and never a closing line; a line has at most one marked part and one greeting;
 * a line of only empty parts is not a line; recency is shared by the whole village.</p>
 */
public final class LineComposer {
    private LineComposer() {}

    public record Speaker(UUID id, String stage, String stageId, String personality, List<ResourceLocation> voices,
                          DialogueVoice.Children children, double flavorTarget) {}

    /** A slot value: its English text, a lang key for other locales, a person if it names one, and grammatical facts. */
    public record SlotValue(String english, @Nullable String key, @Nullable UUID person, Map<String, String> meta) {}

    /**
     * A live thing to talk about. {@code urgency} is how much the speaker wants to raise it: fresh news,
     * a pressing need, a danger. Negative when the listener already knows it.
     */
    public record Subject(ResourceLocation id, String variant, String valence, Map<String, SlotValue> slots,
                          Set<String> provenance, @Nullable Object source, double urgency) {
        public Subject(ResourceLocation id, String variant, String valence, Map<String, SlotValue> slots,
                       Set<String> provenance, @Nullable Object source) {
            this(id, variant, valence, slots, provenance, source, 0);
        }
    }

    /** Per-speaker flavor balance: spent on the next line that has a marked option. */
    public static final class Balance {
        double value;
        boolean lastMarked;
        public Balance(double start) { this.value = start; }
    }

    public interface World {
        boolean requirement(String requirement);
        double gate(ConversationTopic.Gate gate);
        boolean filtered(Set<String> contentTags);
    }

    public interface Recency {
        boolean used(ResourceLocation pool, String key);
        void use(ResourceLocation pool, String key);
        void release(ResourceLocation pool, Collection<String> keys);
    }

    public record Request(ResourceLocation move, Speaker speaker, UUID listener, UUID initiator, @Nullable Subject subject,
                          String register, boolean finalLine, @Nullable ResourceLocation bridgePool, ConversationState state,
                          Balance balance, Random random) {}

    public record Line(ResourceLocation move, DialogueFrame frame, List<DialoguePart> parts, boolean marked) {}

    public static @Nullable Line compose(GenerativeDialogue.Data data, Request req, World world, Recency recency) {
        List<DialogueFrame> frames = new ArrayList<>();
        for (DialogueFrame frame : data.framesByMove().getOrDefault(req.move(), List.of())) {
            if (frame.voice() != null && !req.speaker().voices().contains(frame.voice())) continue;
            if (!frame.subjects().isEmpty() && (req.subject() == null
                    || frame.subjects().stream().noneMatch(f -> data.subjectMatches(req.subject().id(), f)))) continue;
            frames.add(frame);
        }
        if (frames.isEmpty()) return null;
        String pending = req.state().pending();
        ResourceLocation owed = owed(req);
        ConversationPair owedPair = owed == null ? null : data.pairs().get(owed);
        boolean replyRequired = owedPair == null || owedPair.required();
        List<DialogueFrame> withAnswer = frames.stream().filter(DialogueFrame::hasAnswerSlot).toList();
        List<DialogueFrame> without = frames.stream().filter(f -> !f.hasAnswerSlot()).toList();
        if (pending != null && !withAnswer.isEmpty()) frames = withAnswer;
        else if (pending == null && !without.isEmpty()) frames = without;
        DialogueFrame frame = weighted(frames, DialogueFrame::weight, req.random());

        List<DialogueFrame.Slot> slots = new ArrayList<>(frame.slots());
        if (pending != null) {
            if (!frame.hasAnswerSlot()) slots.add(0, DialogueFrame.Slot.ANSWER);
        } else {
            slots.removeIf(DialogueFrame.Slot::answer);
        }
        int narrowed = -1;
        if (owed == null) {
            slots.removeIf(DialogueFrame.Slot::response);
        } else if (!frame.hasResponseSlot() && !slots.isEmpty()) {
            DialogueFrame.Slot head = slots.get(0);
            // A reply pool that cannot answer this pair is the wrong reply, so a fitting one replaces it.
            if (head.pooled() && data.respondingPools().contains(head.pool())) {
                if (respondsIn(data, head.pool(), owed, req.speaker())) narrowed = 0;
                else slots.set(0, DialogueFrame.Slot.RESPONSE);
            } else if (replyRequired || !closing(data, req.move()) && req.random().nextDouble() < owedPair.chance()) {
                slots.add(0, DialogueFrame.Slot.RESPONSE);
            }
        }
        if (req.bridgePool() != null) {
            int at = !slots.isEmpty() && !slots.get(0).pooled() ? 1 : 0;
            slots.add(at, new DialogueFrame.Slot(req.bridgePool(), DialogueFrame.StanceMode.NONE));
        }
        if (slots.isEmpty()) return null;

        ConversationState scratch = req.state().copy();
        scratch.put(ConversationState.ANSWERED, null);
        Balance balance = req.balance();
        double accumulated = balance.value + req.speaker().flavorTarget();
        boolean markedWanted = accumulated >= 1 && !balance.lastMarked;
        List<DialoguePart> chosen = new ArrayList<>();
        boolean usedMarked = false, formSet = false;
        for (int i = 0; i < slots.size(); i++) {
            DialogueFrame.Slot slot = slots.get(i);
            boolean last = i == slots.size() - 1;
            boolean anyText = chosen.stream().anyMatch(p -> !p.empty());
            List<DialoguePart> source = slot.answer()
                    ? data.answersByAspect().getOrDefault(scratch.pending(), List.of())
                    : slot.response() ? data.responsesByPair().getOrDefault(owed, List.of())
                    : data.partsByPool().getOrDefault(slot.pool(), List.of());
            List<DialoguePart> candidates = new ArrayList<>(), unfitting = new ArrayList<>();
            for (DialoguePart part : source) {
                if (!req.speaker().voices().contains(part.voice())) continue;
                if (part.empty() && (!slot.pooled() || last && !anyText)) continue;
                if (part.asks() != null && !last) continue;
                if (part.act() == DialoguePart.Act.GREET && !part.empty()
                        && chosen.stream().anyMatch(p -> p.act() == DialoguePart.Act.GREET && !p.empty())) continue;
                if (!eligible(part, slot.stance(), req, scratch, data, world)) continue;
                if (i == narrowed && !part.responds().contains(owed)) unfitting.add(part);
                else candidates.add(part);
            }
            // An optional reply gives way when nothing fits; a required one fails the line instead.
            if (candidates.isEmpty() && i == narrowed && !replyRequired) candidates = unfitting;
            if (candidates.isEmpty() && slot.response() && !replyRequired) continue;
            if (candidates.isEmpty()) return null;
            List<DialoguePart> marked = candidates.stream().filter(DialoguePart::marked).toList();
            List<DialoguePart> plain = candidates.stream().filter(p -> !p.marked()).toList();
            List<DialoguePart> use;
            if (usedMarked) {
                use = plain;
                if (use.isEmpty()) return null;
            } else if (markedWanted && !marked.isEmpty()) {
                boolean laterHasMarked = false;
                for (DialogueFrame.Slot later : slots.subList(i + 1, slots.size())) {
                    List<DialoguePart> pool = later.answer() ? List.of() : data.partsByPool().getOrDefault(later.pool(), List.of());
                    if (pool.stream().anyMatch(p -> p.marked() && req.speaker().voices().contains(p.voice()))) { laterHasMarked = true; break; }
                }
                use = !laterHasMarked || req.random().nextBoolean() ? marked : (plain.isEmpty() ? marked : plain);
            } else {
                use = plain.isEmpty() ? marked : plain;
            }
            DialoguePart part = pick(use, req, world, recency, scratch);
            usedMarked |= part.marked();
            chosen.add(part);
            if (part.empty()) continue;
            if (part.stance() != null) scratch.put(ConversationState.STANCE, part.stance());
            if (part.statement()) {
                scratch.put(ConversationState.FORM, part.form() == null ? "claim" : part.form());
                formSet = true;
            }
            part.sets().forEach(scratch::put);
            if (part.thread() != null) {
                scratch.put(ConversationState.THREAD, part.thread());
                if (part.stance() != null && !"0".equals(part.stance()))
                    scratch.put(ConversationState.HELD + req.speaker().id() + "." + part.thread(), part.stance());
            }
            for (String arg : part.args()) scratch.put(ConversationState.REVEALED + arg, "true");
            for (String slotName : part.persp().keySet()) scratch.put(ConversationState.REVEALED + slotName, "true");
            if (part.asks() != null) scratch.put(ConversationState.PENDING, part.asks());
            else if (slot.answer()) {
                scratch.put(ConversationState.ANSWERED, scratch.pending());
                scratch.put(ConversationState.PENDING, null);
            }
        }
        List<DialoguePart> spoken = chosen.stream().filter(p -> !p.empty()).toList();
        if (spoken.isEmpty()) return null;
        // The last part that opens a pair sets what the other speaker owes; a question takes its place.
        ResourceLocation opened = null;
        for (DialoguePart part : spoken) if (part.opens() != null) opened = part.opens();
        scratch.clearExpectation();
        if (opened != null && scratch.pending() == null) {
            scratch.put(ConversationState.EXPECTS, opened.toString());
            scratch.put(ConversationState.EXPECTS_BY, req.speaker().id().toString());
            ConversationPair pair = data.pairs().get(opened);
            if (pair == null || pair.required()) scratch.put(ConversationState.EXPECTS_REQUIRED, "true");
        }
        if (!formSet) scratch.put(ConversationState.FORM, null);
        scratch.put(ConversationState.LINES, Integer.toString(scratch.lines() + 1));
        req.state().replaceWith(scratch);
        if (usedMarked) {
            balance.value = accumulated - 1;
            balance.lastMarked = true;
        } else {
            balance.value = Math.min(accumulated, 2);
            balance.lastMarked = false;
        }
        return new Line(req.move(), frame, spoken, usedMarked);
    }

    static boolean eligible(DialoguePart part, DialogueFrame.StanceMode mode, Request req, ConversationState state,
                            GenerativeDialogue.Data data, World world) {
        Speaker speaker = req.speaker();
        Subject subject = req.subject();
        if (!part.stages().isEmpty() && !part.stages().contains(speaker.stage()) && !part.stages().contains(speaker.stageId())) return false;
        if (part.marked() && "child".equals(speaker.stage())) {
            if (speaker.children() == DialogueVoice.Children.NONE) return false;
            if (speaker.children() == DialogueVoice.Children.SIMPLE && !part.simple() && part.stages().isEmpty()) return false;
        }
        if (!part.personalities().isEmpty() && !part.personalities().contains(speaker.personality())) return false;
        if (!part.subjects().isEmpty() && (subject == null
                || part.subjects().stream().noneMatch(f -> data.subjectMatches(subject.id(), f)))) return false;
        if (!part.variants().isEmpty() && (subject == null || !part.variants().contains(subject.variant()))) return false;
        // A register with "!" keeps the part out of that register; plain registers list where it may appear.
        if (!part.registers().isEmpty()) {
            if (part.registers().contains("!" + req.register())) return false;
            if (part.registers().stream().anyMatch(r -> !r.startsWith("!")) && !part.registers().contains(req.register())) return false;
        }
        if (!part.valence().isEmpty() && (subject == null || !part.valence().contains(subject.valence()))) return false;
        if (!part.provenance().isEmpty() && (subject == null
                || part.provenance().stream().noneMatch(subject.provenance()::contains))) return false;
        if (part.minLines() > 0 && state.lines() < part.minLines()) return false;
        if (req.finalLine() && part.asks() != null) return false;
        if (req.finalLine() && part.opens() != null && data.pairs().containsKey(part.opens())
                && data.pairs().get(part.opens()).required()) return false;
        for (var condition : part.state().entrySet()) {
            String have = switch (condition.getKey()) {
                case "variant" -> subject == null ? null : subject.variant();
                case "is_initiator" -> Boolean.toString(speaker.id().equals(req.initiator()));
                default -> state.get(condition.getKey());
            };
            // A value with "!" excludes it: {"after": "!grief"} is any line except right after grave news.
            Set<String> wanted = condition.getValue();
            if (wanted.stream().allMatch(v -> v.startsWith("!"))) {
                if (have != null && wanted.contains("!" + have)) return false;
            } else if (have == null || !wanted.contains(have)) return false;
        }
        if (!part.empty() && mode != DialogueFrame.StanceMode.NONE) {
            String current = state.get(ConversationState.STANCE), mine = part.stance();
            if (mode == DialogueFrame.StanceMode.MATCH && current != null && mine != null && !mine.equals(current)) return false;
            if (mode == DialogueFrame.StanceMode.OPPOSE && (current == null || "0".equals(current) || mine == null
                    || !mine.equals("+".equals(current) ? "-" : "+"))) return false;
        }
        if (part.about() != null && part.asks() != null && state.revealed(part.about())) return false;
        // Nobody can answer a question about something the subject does not include, such as a place nobody noted.
        if (part.about() != null && part.asks() != null && (subject == null || !subject.slots().containsKey(part.about()))) return false;
        // A speaker keeps the stance they took on a thread for the rest of the subject.
        if (part.thread() != null && part.stance() != null && !"0".equals(part.stance())) {
            String held = state.get(ConversationState.HELD + speaker.id() + "." + part.thread());
            if (held != null && !"0".equals(held) && !held.equals(part.stance())) return false;
        }
        for (String requirement : part.requires()) if (requirement.startsWith("!") == world.requirement(requirement.replaceFirst("^!", ""))) return false;
        for (var persp : part.persp().entrySet()) {
            SlotValue value = subject == null ? null : subject.slots().get(persp.getKey());
            if (value == null || value.person() == null) return false;
            if (!relation(value.person(), speaker.id(), req.listener()).equals(persp.getValue())) return false;
            if (!part.introduces()) {
                boolean revealed = state.revealed(persp.getKey());
                if (part.asks() != null ? revealed : !revealed) return false;
            }
        }
        for (String arg : part.args()) {
            if (arg.equals("other") || arg.equals("self")) continue;
            SlotValue value = subject == null ? null : subject.slots().get(arg);
            if (value == null) return false;
            if (value.person() != null && !part.persp().containsKey(arg)
                    && !relation(value.person(), speaker.id(), req.listener()).equals("they")) return false;
        }
        if (!part.contentTags().isEmpty() && world.filtered(part.contentTags())) return false;
        return part.gate().isOpen() || world.gate(part.gate()) > 0;
    }

    /** The pair this speaker owes a reply to: opened by the other speaker, with no question in the way. */
    static @Nullable ResourceLocation owed(Request req) {
        ConversationState state = req.state();
        String expects = state.get(ConversationState.EXPECTS);
        if (expects == null || state.pending() != null || req.speaker().id().toString().equals(state.get(ConversationState.EXPECTS_BY))) return null;
        return ResourceLocation.tryParse(expects);
    }

    /** A goodbye does not open with an optional reply: "Right? I think I'll call it a night." */
    private static boolean closing(GenerativeDialogue.Data data, ResourceLocation move) {
        ConversationMove def = data.moves().get(move);
        return def != null && def.role() == ConversationMove.Role.CLOSING;
    }

    private static boolean respondsIn(GenerativeDialogue.Data data, ResourceLocation pool, ResourceLocation pair, Speaker speaker) {
        for (DialoguePart part : data.partsByPool().getOrDefault(pool, List.of()))
            if (part.responds().contains(pair) && speaker.voices().contains(part.voice())) return true;
        return false;
    }

    static String relation(UUID person, UUID speaker, UUID listener) {
        return person.equals(speaker) ? "me" : person.equals(listener) ? "you" : "they";
    }

    /**
     * Selection weight: authored weight, gate, then a boost for parts written for a narrower moment,
     * then a preference for parts that continue the thread the last line left open.
     */
    static double score(DialoguePart part, World world, ConversationState state) {
        double weight = part.weight() * (part.gate().isOpen() ? 1 : world.gate(part.gate()));
        if (part.empty()) return weight;
        weight *= 1 + SPECIFICITY * part.specificity();
        String thread = state.get(ConversationState.THREAD);
        if (thread != null && part.thread() != null) {
            if (part.thread().equals(thread)) weight *= SAME_THREAD;
            else if (DialoguePart.threadRoot(part.thread()).equals(DialoguePart.threadRoot(thread))) weight *= RELATED_THREAD;
            else weight *= OTHER_THREAD;
        }
        return weight;
    }

    static final double SPECIFICITY = 0.5, SAME_THREAD = 5, RELATED_THREAD = 2.5, OTHER_THREAD = 0.5;

    private static DialoguePart pick(List<DialoguePart> candidates, Request req, World world, Recency recency, ConversationState state) {
        List<DialoguePart> fresh = new ArrayList<>();
        for (DialoguePart part : candidates) if (part.empty() || !recency.used(part.pool(), part.key())) fresh.add(part);
        if (fresh.stream().noneMatch(p -> !p.empty()) && candidates.stream().anyMatch(p -> !p.empty())) {
            Map<ResourceLocation, List<String>> byPool = new LinkedHashMap<>();
            for (DialoguePart part : candidates) if (!part.empty()) byPool.computeIfAbsent(part.pool(), k -> new ArrayList<>()).add(part.key());
            byPool.forEach(recency::release);
            fresh = candidates;
        }
        DialoguePart chosen = weighted(fresh, part -> score(part, world, state), req.random());
        if (!chosen.empty()) recency.use(chosen.pool(), chosen.key());
        return chosen;
    }

    static <T> T weighted(List<T> options, java.util.function.ToDoubleFunction<T> weight, Random random) {
        double total = 0;
        double[] w = new double[options.size()];
        for (int i = 0; i < w.length; i++) {
            double value = weight.applyAsDouble(options.get(i));
            w[i] = Double.isFinite(value) && value > 0 ? value : 0;
            total += w[i];
        }
        if (total <= 0) return options.get(random.nextInt(options.size()));
        double roll = random.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) return options.get(i);
        }
        return options.get(options.size() - 1);
    }
}
