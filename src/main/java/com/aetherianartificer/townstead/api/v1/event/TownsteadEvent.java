package com.aetherianartificer.townstead.api.v1.event;

/**
 * Marker for every event Townstead posts through {@code EventsApi}. Events are immutable
 * records, posted on the server thread after the change they describe has committed, and never
 * cancellable.
 */
public interface TownsteadEvent {
}
