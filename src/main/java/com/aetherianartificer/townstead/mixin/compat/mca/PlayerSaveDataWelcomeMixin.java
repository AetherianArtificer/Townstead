package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsStore;
import com.aetherianartificer.townstead.compat.travelerstitles.VillageEnterTitlePayload;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Optional;

@Mixin(PlayerSaveData.class)
public abstract class PlayerSaveDataWelcomeMixin {

    @Redirect(
            method = "onEnter",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;displayClientMessage(Lnet/minecraft/network/chat/Component;Z)V"
            ),
            remap = false
    )
    private void townstead$interceptVillageWelcome(Player player, Component message, boolean actionBar) {
        if (player instanceof ServerPlayer sp
                && ClientCapsStore.hasTravelersTitles(sp.getUUID())
                && sp.level() instanceof ServerLevel level) {
            Optional<Village> villageOpt = VillageManager.get(level).findNearestVillage(sp);
            if (villageOpt.isPresent()) {
                Village village = villageOpt.get();
                int population = village.getResidents(level).size();
                VillageEnterTitlePayload payload =
                        new VillageEnterTitlePayload(village.getName(), population);
                //? if neoforge {
                PacketDistributor.sendToPlayer(sp, payload);
                //?} else if forge {
                /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, payload);
                *///?}
                return;
            }
        }
        player.displayClientMessage(message, actionBar);
    }
}
