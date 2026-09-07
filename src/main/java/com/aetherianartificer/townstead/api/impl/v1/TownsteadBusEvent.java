package com.aetherianartificer.townstead.api.impl.v1;

import com.aetherianartificer.townstead.api.v1.event.TownsteadEvent;

//? if neoforge {
import net.neoforged.bus.api.Event;
//?} else if forge {
/*import net.minecraftforge.eventbus.api.Event;
*///?}

/**
 * The loader-bus carrier for {@code api.v1} events. Subscribe to this class on the Forge or
 * NeoForge bus and read {@link #payload()}; the payload record is the stable part. Never
 * cancellable. Posted after the API's own listeners have run.
 */
public final class TownsteadBusEvent extends Event {
    private final TownsteadEvent payload;

    TownsteadBusEvent(TownsteadEvent payload) {
        this.payload = payload;
    }

    public TownsteadEvent payload() {
        return payload;
    }

    public <E extends TownsteadEvent> boolean is(Class<E> type) {
        return type.isInstance(payload);
    }

    public <E extends TownsteadEvent> E as(Class<E> type) {
        return type.cast(payload);
    }

    static boolean mirrored() {
        return true;
    }

    static void mirror(TownsteadEvent event) {
        //? if neoforge {
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new TownsteadBusEvent(event));
        //?} else if forge {
        /*net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new TownsteadBusEvent(event));
        *///?}
    }
}
