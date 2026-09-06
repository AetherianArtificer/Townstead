package com.aetherianartificer.townstead.dialogue.conversation;

import java.util.*;
import java.util.function.ToDoubleFunction;

/** Pure turn state. A terminal effect is committed only after its last spoken turn completes. */
public final class ConversationSession {
    public enum Status { READY, WAITING, COMPLETE, CANCELLED }
    private final ConversationTopic topic;
    private final UUID initiator, responder;
    private final long deadline;
    private String turn;
    private long nextAt;
    private Status status = Status.READY;
    private String reason = "";

    public ConversationSession(ConversationTopic topic, UUID initiator, UUID responder, long now) {
        if (initiator.equals(responder)) throw new IllegalArgumentException("conversation requires two different participants");
        this.topic = topic; this.initiator = initiator; this.responder = responder;
        this.turn = topic.start(); nextAt = now; deadline = now + 1800;
    }
    public UUID initiator() { return initiator; }
    public UUID responder() { return responder; }
    public ConversationTopic topic() { return topic; }
    public ConversationTopic.Turn turn() { return topic.turns().get(turn); }
    public UUID speaker() { return turn().speaker() == ConversationTopic.Speaker.INITIATOR ? initiator : responder; }
    public UUID listener() { return speaker().equals(initiator) ? responder : initiator; }
    public Status status() { return status; }
    public String reason() { return reason; }
    public boolean due(long now) { return status == Status.READY && now >= nextAt; }
    public void spoken(long now) {
        if (!due(now)) throw new IllegalStateException("turn is not ready");
        nextAt = now + turn().duration(); status = Status.WAITING;
    }
    public void advance(long now, boolean available, ToDoubleFunction<ConversationTopic.Turn> weight, Random random) {
        if (status == Status.COMPLETE || status == Status.CANCELLED) return;
        if (!available) { cancel("participant_unavailable"); return; }
        if (now >= deadline) { cancel("timeout"); return; }
        if (status != Status.WAITING || now < nextAt) return;
        if (turn().outcome() != null) { status = Status.COMPLETE; return; }
        List<ConversationTopic.Turn> choices = turn().replies().stream().map(topic.turns()::get).toList();
        ConversationTopic.Turn next = choose(choices, weight, random);
        if (next == null) { cancel("no_eligible_reply"); return; }
        turn = next.id(); status = Status.READY;
    }
    public void cancel(String reason) {
        if (status == Status.COMPLETE || status == Status.CANCELLED) return;
        status = Status.CANCELLED; this.reason = reason;
    }
    public ConversationTopic.Outcome outcome() { return status == Status.COMPLETE ? turn().outcome() : null; }
    public static <T> T choose(List<T> options, ToDoubleFunction<T> weights, Random random) {
        double[] values = new double[options.size()]; double total = 0;
        for (int i = 0; i < values.length; i++) {
            double value = weights.applyAsDouble(options.get(i));
            values[i] = Double.isFinite(value) && value > 0 ? value : 0; total += values[i];
        }
        if (total <= 0) return null;
        double roll = random.nextDouble() * total;
        for (int i = 0; i < options.size(); i++) { roll -= values[i]; if (roll < 0) return options.get(i); }
        return options.get(options.size() - 1);
    }
}
