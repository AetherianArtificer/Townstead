package com.aetherianartificer.townstead.dialogue.conversation.generative;

import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a conversation has established so far. Parts read it through {@code state} conditions and
 * change it through {@code sets}; the composer also writes the reserved keys below.
 */
public final class ConversationState {
    public static final String PENDING = "pending", ANSWERED = "answered", STANCE = "stance", FORM = "form",
            LINES = "lines", ASSOC = "assoc", VENUE = "venue", REVEALED = "revealed.";
    /** The pair the last line opened, who opened it, and whether it must be answered. */
    public static final String EXPECTS = "expects", EXPECTS_BY = "expects.by", EXPECTS_REQUIRED = "expects.required";
    /** The thread the last line left open, and each speaker's stance on a thread: {@code held.<speaker>.<thread>}. */
    public static final String THREAD = "thread", HELD = "held.";
    /** How the conversation feels: warm, tense or flat. Parts can require it with a state condition. */
    public static final String MOOD = "mood";
    /** The register of the topic the talk just left. */
    public static final String AFTER = "after";

    private final Map<String, String> values;

    public ConversationState() { this(new LinkedHashMap<>()); }
    private ConversationState(Map<String, String> values) { this.values = values; }

    public @Nullable String get(String key) { return values.get(key); }
    public void put(String key, @Nullable String value) {
        if (value == null) values.remove(key); else values.put(key, value);
    }
    public boolean revealed(String slot) { return "true".equals(values.get(REVEALED + slot)); }
    public @Nullable String pending() { return values.get(PENDING); }
    public boolean replyOwed() { return values.containsKey(EXPECTS_REQUIRED); }
    public void clearExpectation() {
        values.remove(EXPECTS);
        values.remove(EXPECTS_BY);
        values.remove(EXPECTS_REQUIRED);
    }
    public int lines() {
        String raw = values.get(LINES);
        return raw == null ? 0 : Integer.parseInt(raw);
    }
    public ConversationState copy() { return new ConversationState(new LinkedHashMap<>(values)); }
    public void replaceWith(ConversationState other) {
        values.clear();
        values.putAll(other.values);
    }

    /** Clears what belongs to one subject when the talk moves on; keeps what is owed and the line count. */
    public void newSubject() {
        String pending = values.get(PENDING), lines = values.get(LINES), venue = values.get(VENUE), mood = values.get(MOOD);
        Map<String, String> owed = new LinkedHashMap<>();
        if (replyOwed()) for (String key : new String[]{EXPECTS, EXPECTS_BY, EXPECTS_REQUIRED}) owed.put(key, values.get(key));
        values.clear();
        values.putAll(owed);
        put(PENDING, pending);
        put(LINES, lines);
        put(VENUE, venue);
        put(MOOD, mood);
    }

    public Map<String, String> view() { return Map.copyOf(values); }
}
