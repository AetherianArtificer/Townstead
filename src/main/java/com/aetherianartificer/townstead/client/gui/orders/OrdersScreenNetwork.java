package com.aetherianartificer.townstead.client.gui.orders;

import com.aetherianartificer.townstead.work.order.net.OrderEditC2SPayload;

/**
 * The one place the orders screen talks to the server, so the loader split lives in a single method
 * instead of at every button.
 */
final class OrdersScreenNetwork {

    private OrdersScreenNetwork() {}

    static void send(OrderEditC2SPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
