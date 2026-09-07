package com.aetherianartificer.townstead.api.v1;

import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;

import java.util.function.Consumer;

/**
 * Townstead's event feed. Listeners run synchronously on the server thread after the change has
 * committed, so a listener may safely read back through the API. A listener that throws is logged
 * and skipped; it can never break a Townstead tick. Events are never cancellable.
 *
 * <p>The same events are also mirrored onto the loader's event bus, wrapped in a loader-specific
 * carrier whose {@code payload()} is the record from {@code api.v1.event}.
 */
public interface EventsApi {

    /** Subscribes to one event type. Close the subscription to stop listening. */
    <E extends TownsteadEvent> Subscription subscribe(Class<E> type, Consumer<E> listener);
}
