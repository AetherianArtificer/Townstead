package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.RootMix;
import com.aetherianartificer.townstead.root.RootMixC2SPayload;
import com.aetherianartificer.townstead.root.RootMixS2CPayload;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * What each mix is called, as the server named it. Heritage profiles live server-side, so the first
 * look at a mix asks, and the answer lands a moment later; {@link #get} returns null until then.
 */
public final class RootMixNames {

    /** A mix's name and backstory; an empty name means the server refused the mix. */
    public record Named(String name, String backstory) {}

    private static final Map<RootMix, Named> NAMED = new HashMap<>();
    private static final Map<Integer, RootMix> ASKED = new HashMap<>();
    private static int nextRequest;
    private static @Nullable Runnable listener;

    private RootMixNames() {}

    /** The mix's name, or null while the server is still answering (asking it on the first call). */
    public static @Nullable Named get(RootMix mix) {
        Named named = NAMED.get(mix);
        if (named == null && !ASKED.containsValue(mix)) {
            int id = ++nextRequest;
            ASKED.put(id, mix);
            send(new RootMixC2SPayload(-1, id, false, mix));
        }
        return named;
    }

    /** Rolls a founder of {@code mix} onto {@code target}, as a root Apply does. */
    public static void apply(int target, RootMix mix) {
        send(new RootMixC2SPayload(target, 0, true, mix));
    }

    /** Runs {@code onAnswer} whenever a name lands; null stops. Names are forgotten on every new listener. */
    public static void listen(@Nullable Runnable onAnswer) {
        if (onAnswer != null) {
            NAMED.clear();
            ASKED.clear();
        }
        listener = onAnswer;
    }

    public static void accept(RootMixS2CPayload payload) {
        RootMix mix = ASKED.remove(payload.requestId());
        if (mix == null) return;
        NAMED.put(mix, new Named(payload.name(), payload.backstory()));
        Runnable onAnswer = listener;
        if (onAnswer != null) onAnswer.run();
    }

    private static void send(RootMixC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
