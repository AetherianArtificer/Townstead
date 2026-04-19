package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.compat.travelerstitles.ClientCapsStore;
import com.aetherianartificer.townstead.compat.travelerstitles.VillageEnterTitlePayload;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerSaveData.class)
public abstract class PlayerSaveDataWelcomeMixin {

    @Inject(
            method = "onEnter",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void townstead$maybeRedirectWelcome(Player player, Village village, CallbackInfo ci) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!ClientCapsStore.hasTravelersTitles(sp.getUUID())) return;
        if (!(sp.level() instanceof ServerLevel level)) return;

        int population = village.getResidents(level).size();
        VillageEnterTitlePayload payload =
                new VillageEnterTitlePayload(village.getName(), population);
        //? if neoforge {
        PacketDistributor.sendToPlayer(sp, payload);
        //?} else if forge {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, payload);
        *///?}

        // Preserve the non-welcome side effect MCA's onEnter performs after the actionbar.
        village.onEnter(level);
        ci.cancel();
    }
}
