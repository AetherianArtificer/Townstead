package com.aetherianartificer.townstead.reaction.trigger;

/**
 * Marker interface for parsed trigger entries. Implementations are records
 * owned by their respective {@link TriggerType}. Trigger sources hand the
 * dispatcher a (type, key) pair, and the dispatcher looks up matching
 * reactions via the {@link com.aetherianartificer.townstead.reaction.TriggerIndex}.
 */
public interface TriggerInstance {
    /**
     * The wire {@code type} string. Used by the index for the outer
     * (type, key) lookup.
     */
    String typeKey();
}
