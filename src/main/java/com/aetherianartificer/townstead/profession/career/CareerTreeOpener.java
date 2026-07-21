package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.profession.ProfessionSlotRules;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Server-side entry points for the Career screen. The diegetic path is the "Careers"
 * conversation option on the Archives' Scribe, available during their scheduled work
 * hours; the server re-validates every request before rendering and sending the tree.
 */
public final class CareerTreeOpener {

    private static final String SCRIBE_ID = "townstead:scribe";
    private static final double CONSULT_RANGE = 8.0;

    private CareerTreeOpener() {}

    public static boolean isScribe(VillagerEntityMCA villager) {
        return SCRIBE_ID.equals(ProfessionSlotRules.professionKey(
                villager.getVillagerData().getProfession()));
    }

    /** Office hours are the counsellor's own schedule saying WORK, via the shared resolver. */
    public static boolean isOnDuty(VillagerEntityMCA villager) {
        if (villager.level().isClientSide) {
            return com.aetherianartificer.townstead.shift.VillagerSchedules.clientMirrorActivity(
                    villager.getUUID(), villager.level().getDayTime())
                    == net.minecraft.world.entity.schedule.Activity.WORK;
        }
        return com.aetherianartificer.townstead.shift.VillagerSchedules.isWorking(villager);
    }

    /** The "Careers" conversation option: validate the Scribe consult, then answer. */
    public static void handleRequest(ServerPlayer player, int villagerId) {
        Entity entity = player.serverLevel().getEntity(villagerId);
        if (!(entity instanceof VillagerEntityMCA villager)
                || !villager.isAlive()
                || player.distanceTo(villager) > CONSULT_RANGE
                || !isScribe(villager)) {
            return;
        }
        if (!isOnDuty(villager)) {
            player.displayClientMessage(
                    Component.translatable("townstead.career.scribe.off_duty"), false);
            return;
        }
        send(player);
    }

    public static void send(ServerPlayer player) {
        CareerGraphS2CPayload payload = new CareerGraphS2CPayload(
                player.getName().getString(),
                CareerGraphBuilder.build(player.getServer(), player));
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(player, payload);
        *///?}
    }
}
