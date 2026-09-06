package com.aetherianartificer.townstead.dialogue.conversation;

import com.google.gson.*;
import java.util.*;

/** Bounded pair history with directional opinions. Only completed exchanges can change it. */
public final class ConversationMemory {
    public static final int MAX_PAIRS = 4096;
    private static final long DAY = 24000L, RETENTION = 30 * DAY;
    private record Pair(UUID first, UUID second) {
        static Pair of(UUID a, UUID b) { return a.compareTo(b) < 0 ? new Pair(a, b) : new Pair(b, a); }
    }
    private static final class Memory {
        int firstOpinion, secondOpinion, meetings, rewarded;
        long lastAt, rewardDay = -1;
        String firstName = "", secondName = "", firstMemory = "", secondMemory = "";
        final Deque<String> topics = new ArrayDeque<>();
    }
    private final Map<Pair, Memory> pairs = new LinkedHashMap<>();

    public record Completion(boolean rewarded, String operationId) {}
    public record LegacyOpinion(UUID from, UUID toward, int opinion, long lastAt) {}
    public record Companion(UUID other, String name, int meetings) {}

    public record View(int opinion, int meetings, long lastAt, String memory, List<String> topics) {
        public Set<String> relationships() {
            Set<String> out = new LinkedHashSet<>();
            out.add(meetings == 0 ? "stranger" : "familiar");
            if (opinion >= 12 && meetings >= 4) out.add("friend");
            if (opinion <= -8) out.add("strained");
            return Set.copyOf(out);
        }
    }
    public View view(UUID actor, UUID other, long now) {
        Pair key = Pair.of(actor, other); Memory m = pairs.get(key);
        if (m == null || now - m.lastAt >= RETENTION) return new View(0, 0, Long.MIN_VALUE, "", List.of());
        return new View(key.first.equals(actor) ? m.firstOpinion : m.secondOpinion,
                m.meetings, m.lastAt, key.first.equals(actor) ? m.firstMemory : m.secondMemory, List.copyOf(m.topics));
    }
    /** Returns whether this exchange may also apply mood/Chronicle effects (two per pair/day). */
    public boolean complete(UUID initiator, String initiatorName, UUID responder, String responderName,
                            String topic, ConversationTopic.Outcome outcome, long now) {
        return completeDetailed(initiator, initiatorName, responder, responderName, topic, outcome, now).rewarded();
    }

    public Completion completeDetailed(UUID initiator, String initiatorName, UUID responder, String responderName,
                            String topic, ConversationTopic.Outcome outcome, long now) {
        return completeDetailed(initiator, initiatorName, responder, responderName, topic, outcome, now, true);
    }

    /** updateLegacyOpinion is false for the live v2 path; the old scalar remains frozen migration evidence. */
    public Completion completeDetailed(UUID initiator, String initiatorName, UUID responder, String responderName,
                            String topic, ConversationTopic.Outcome outcome, long now, boolean updateLegacyOpinion) {
        if (initiator.equals(responder) || outcome == null) return new Completion(false, "");
        prune(now);
        Pair key = Pair.of(initiator, responder);
        Memory m = pairs.computeIfAbsent(key, ignored -> new Memory());
        boolean forward = key.first.equals(initiator);
        m.firstName = bounded(forward ? initiatorName : responderName); m.secondName = bounded(forward ? responderName : initiatorName);
        long day = Math.floorDiv(now, DAY);
        if (m.rewardDay != day) { m.rewardDay = day; m.rewarded = 0; }
        boolean reward = m.rewarded < 2 && mayReward(initiator, day) && mayReward(responder, day);
        if (reward) {
            if (updateLegacyOpinion) {
                m.firstOpinion = clamp(m.firstOpinion + (forward ? outcome.initiatorOpinion() : outcome.responderOpinion()));
                m.secondOpinion = clamp(m.secondOpinion + (forward ? outcome.responderOpinion() : outcome.initiatorOpinion()));
            }
            m.rewarded++;
        }
        m.meetings = Math.min(10000, m.meetings + 1); m.lastAt = now;
        m.firstMemory = bounded(forward ? outcome.initiatorMemory() : outcome.responderMemory());
        m.secondMemory = bounded(forward ? outcome.responderMemory() : outcome.initiatorMemory());
        m.topics.addLast(bounded(topic)); while (m.topics.size() > 8) m.topics.removeFirst();
        trim();
        String operation = reward ? "conversation:" + key.first + ":" + key.second + ":" + day + ":" + m.rewarded : "";
        return new Completion(reward, operation);
    }

    /** One-time migration view. The values remain serialized as recovery evidence. */
    public List<LegacyOpinion> legacyOpinions() {
        List<LegacyOpinion> out = new ArrayList<>();
        pairs.forEach((pair, memory) -> {
            if (memory.firstOpinion != 0) out.add(new LegacyOpinion(pair.first, pair.second, memory.firstOpinion, memory.lastAt));
            if (memory.secondOpinion != 0) out.add(new LegacyOpinion(pair.second, pair.first, memory.secondOpinion, memory.lastAt));
        });
        return List.copyOf(out);
    }
    public List<Companion> companions(UUID actor) {
        List<Companion> out = new ArrayList<>();
        pairs.forEach((pair, memory) -> {
            if (pair.first.equals(actor)) out.add(new Companion(pair.second, memory.secondName, memory.meetings));
            else if (pair.second.equals(actor)) out.add(new Companion(pair.first, memory.firstName, memory.meetings));
        });
        return List.copyOf(out);
    }
    public Map<UUID, String> friends(UUID actor, long now) {
        Map<UUID, String> out = new LinkedHashMap<>();
        pairs.forEach((pair, memory) -> {
            if (now - memory.lastAt >= RETENTION || memory.meetings < 4
                    || memory.firstOpinion < 12 || memory.secondOpinion < 12) return;
            if (pair.first.equals(actor)) out.put(pair.second, memory.secondName);
            else if (pair.second.equals(actor)) out.put(pair.first, memory.firstName);
        });
        return Map.copyOf(out);
    }
    public void prune(long now) { pairs.entrySet().removeIf(e -> now - e.getValue().lastAt >= RETENTION); }
    private void trim() {
        while (pairs.size() > MAX_PAIRS) {
            Pair oldest = pairs.entrySet().stream().min(Comparator.comparingLong(e -> e.getValue().lastAt)).orElseThrow().getKey();
            pairs.remove(oldest);
        }
    }
    private static int clamp(int value) { return Math.max(-30, Math.min(30, value)); }
    private static String bounded(String value) { return value == null ? "" : value.substring(0, Math.min(256, value.length())); }
    private boolean mayReward(UUID actor, long day) {
        return pairs.entrySet().stream().filter(e -> e.getValue().rewardDay == day
                && (e.getKey().first.equals(actor) || e.getKey().second.equals(actor)))
                .mapToInt(e -> e.getValue().rewarded).sum() < 4;
    }
    /** Portable snapshot; SavedData stores this bounded document inside its NBT envelope. */
    public JsonObject save() {
        JsonObject root = new JsonObject(); JsonArray entries = new JsonArray();
        pairs.forEach((pair, m) -> {
            JsonObject tag = new JsonObject(); tag.addProperty("first", pair.first.toString()); tag.addProperty("second", pair.second.toString());
            tag.addProperty("firstOpinion", m.firstOpinion); tag.addProperty("secondOpinion", m.secondOpinion);
            tag.addProperty("meetings", m.meetings); tag.addProperty("rewarded", m.rewarded); tag.addProperty("rewardDay", m.rewardDay);
            tag.addProperty("lastAt", m.lastAt); tag.addProperty("firstMemory", m.firstMemory); tag.addProperty("secondMemory", m.secondMemory);
            tag.addProperty("firstName", m.firstName); tag.addProperty("secondName", m.secondName);
            JsonArray topics = new JsonArray(); m.topics.forEach(topics::add); tag.add("topics", topics);
            entries.add(tag);
        });
        root.addProperty("version", 1); root.add("pairs", entries); return root;
    }
    public static ConversationMemory load(JsonObject root) {
        ConversationMemory result = new ConversationMemory();
        if (!root.has("version") || root.get("version").getAsInt() != 1) throw new IllegalArgumentException("unsupported conversation memory version");
        JsonArray entries = root.getAsJsonArray("pairs");
        for (int i = 0; i < entries.size() && i < MAX_PAIRS; i++) {
            JsonObject tag = entries.get(i).getAsJsonObject();
            UUID a = UUID.fromString(tag.get("first").getAsString()), b = UUID.fromString(tag.get("second").getAsString());
            if (a.equals(b)) continue;
            Memory m = new Memory();
            boolean forward = a.compareTo(b) < 0;
            m.firstOpinion = clamp(tag.get(forward ? "firstOpinion" : "secondOpinion").getAsInt());
            m.secondOpinion = clamp(tag.get(forward ? "secondOpinion" : "firstOpinion").getAsInt());
            m.firstName = bounded(tag.get(forward ? "firstName" : "secondName").getAsString());
            m.secondName = bounded(tag.get(forward ? "secondName" : "firstName").getAsString());
            m.meetings = Math.max(0, Math.min(10000, tag.get("meetings").getAsInt()));
            m.rewarded = Math.max(0, Math.min(2, tag.get("rewarded").getAsInt())); m.rewardDay = tag.get("rewardDay").getAsLong();
            m.lastAt = tag.get("lastAt").getAsLong();
            m.firstMemory = bounded(tag.get(forward ? "firstMemory" : "secondMemory").getAsString());
            m.secondMemory = bounded(tag.get(forward ? "secondMemory" : "firstMemory").getAsString());
            JsonArray topics = tag.getAsJsonArray("topics");
            for (int j = Math.max(0, topics.size() - 8); j < topics.size(); j++) m.topics.addLast(bounded(topics.get(j).getAsString()));
            result.pairs.put(Pair.of(a, b), m);
        }
        return result;
    }
}
