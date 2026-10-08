package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.EventsApi;
import com.aetherianartificer.townstead.api.v1.event.Subscription;
import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;

import java.util.function.Consumer;

final class EventsImpl implements EventsApi {

    @Override
    public <E extends TownsteadEvent> Subscription subscribe(Class<E> type, Consumer<E> listener) {
        return ApiEvents.subscribe(type, listener);
    }
}
