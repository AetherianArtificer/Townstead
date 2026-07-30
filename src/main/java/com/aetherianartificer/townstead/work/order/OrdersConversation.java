package com.aetherianartificer.townstead.work.order;

import com.aetherianartificer.townstead.work.order.net.OrdersOfferS2CPayload;
import com.aetherianartificer.townstead.work.site.Worksite;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * The conversation door: asking a worker, at their worksite, during their hours.
 *
 * <p>The free door, and the one with the most conditions on it — which is the point. An Order Board
 * costs planks and answers at midnight; a worker answers for nothing but only while they are
 * actually at work. Both open the same list.</p>
 *
 * <p>Availability is decided here and sent, never guessed at on the client. Whether a room is a
 * registered worksite is server knowledge, and an option that appears and then dead-ends is worse
 * than one that never appears.</p>
 */
public final class OrdersConversation {

    /** Close enough to be talking to them, matched to the Scribe's consult range. */
    private static final double TALK_RANGE = 8.0;

    private OrdersConversation() {}

    /** Answers "can I ask them about orders?", so the dialogue can offer the option or not. */
    public static void offer(ServerPlayer player, int villagerId) {
        send(player, new OrdersOfferS2CPayload(villagerId, siteFor(player, villagerId) != null));
    }

    /** Answers "ask them", re-validating everything the offer checked a moment ago. */
    public static void open(ServerPlayer player, int villagerId) {
        Worksite site = siteFor(player, villagerId);
        if (site == null) {
            player.displayClientMessage(
                    Component.translatable("townstead.orders.villager.unavailable"), false);
            return;
        }
        OrdersOpener.open(player, site);
    }

    /**
     * The worksite this villager can speak for right now: the one they are standing in, while they
     * are on shift. Their position is the honest answer — a cook walking home is not at a kitchen,
     * and asking them about it there would be asking about a place neither of you is in.
     */
    @Nullable
    private static Worksite siteFor(ServerPlayer player, int villagerId) {
        ServerLevel level = player.serverLevel();
        Entity entity = level.getEntity(villagerId);
        if (!(entity instanceof VillagerEntityMCA villager)
                || !villager.isAlive()
                || player.distanceTo(villager) > TALK_RANGE
                || !com.aetherianartificer.townstead.shift.VillagerSchedules.isWorking(villager)) {
            return null;
        }
        return OrdersOpener.siteAt(level, villager.blockPosition(), false);
    }

    private static void send(ServerPlayer player, OrdersOfferS2CPayload payload) {
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
