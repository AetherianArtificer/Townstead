package com.aetherianartificer.townstead.client.root;

import com.aetherianartificer.townstead.root.RootFoundingC2SPayload;
import com.aetherianartificer.townstead.root.RootFoundingS2CPayload;
import com.aetherianartificer.townstead.root.RootMix;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Sends the editor's founding requests and hands each answer to whoever is listening, normally the
 * open editor's {@code RootEditSession}. Answers carry their request id, so a listener can drop the
 * ones it no longer wants.
 */
public final class RootFoundingClient {

    private static int nextRequest;
    private static @Nullable Consumer<RootFoundingS2CPayload> listener;

    private RootFoundingClient() {}

    public static void listen(@Nullable Consumer<RootFoundingS2CPayload> onAnswer) {
        listener = onAnswer;
    }

    /** Asks what {@code target} is now. Returns the request id. */
    public static int askState(int target) {
        return send(target, RootFoundingC2SPayload.STATE, "", RootMix.NONE, 0);
    }

    /** Asks for a founding of one root ({@code mix} null) or a mix. Returns the request id. */
    public static int roll(int target, String rootId, @Nullable RootMix mix) {
        return send(target, RootFoundingC2SPayload.ROLL, rootId, mix == null ? RootMix.NONE : mix, 0);
    }

    /** Gives {@code target} the founding as it was shown. Returns the request id. */
    public static int commit(int target, int foundingId) {
        return send(target, RootFoundingC2SPayload.COMMIT, "", RootMix.NONE, foundingId);
    }

    public static void accept(RootFoundingS2CPayload payload) {
        Consumer<RootFoundingS2CPayload> onAnswer = listener;
        if (onAnswer != null) onAnswer.accept(payload);
    }

    private static int send(int target, int op, String rootId, RootMix mix, int foundingId) {
        int id = ++nextRequest;
        RootFoundingC2SPayload payload = new RootFoundingC2SPayload(target, id, op, rootId, mix, foundingId);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
        return id;
    }
}
