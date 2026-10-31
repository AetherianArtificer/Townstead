package com.aetherianartificer.townstead.client.seal;

import com.aetherianartificer.townstead.seal.PersonalSeal;
import com.aetherianartificer.townstead.seal.SealC2SPayload;

/** This player's own seal, as the server last confirmed it. Choosing one applies here at once. */
public final class ClientSeal {
    private static PersonalSeal seal = PersonalSeal.DEFAULT;

    private ClientSeal() {}

    public static PersonalSeal get() { return seal; }

    public static void set(PersonalSeal value) { seal = value == null ? PersonalSeal.DEFAULT : value; }

    public static void choose(String device, int dye) {
        PersonalSeal chosen = PersonalSeal.sanitized(device, dye);
        if (chosen == null) return;
        seal = chosen;
        SealC2SPayload payload = new SealC2SPayload(device, dye);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
