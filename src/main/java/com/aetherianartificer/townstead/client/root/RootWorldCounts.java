package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.RootCountsC2SPayload;

import java.util.Map;

/**
 * How many of each root live in this world, as the server last counted them. The root browser asks
 * when it opens and redraws its "In this world" shortcut when the answer lands.
 */
public final class RootWorldCounts {

    private static volatile Map<String, Integer> COUNTS = Map.of();
    private static volatile Runnable listener;

    private RootWorldCounts() {}

    public static Map<String, Integer> counts() {
        return COUNTS;
    }

    /** Asks the server for fresh counts; {@code onAnswer} runs when they arrive. */
    public static void request(Runnable onAnswer) {
        listener = onAnswer;
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new RootCountsC2SPayload());
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(new RootCountsC2SPayload());
        *///?}
    }

    public static void stopListening() {
        listener = null;
    }

    public static void accept(Map<String, Integer> counts) {
        COUNTS = Map.copyOf(counts);
        Runnable onAnswer = listener;
        if (onAnswer != null) onAnswer.run();
    }
}
