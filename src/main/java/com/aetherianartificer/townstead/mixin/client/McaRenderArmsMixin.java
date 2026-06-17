package com.aetherianartificer.townstead.mixin.client;

import com.aetherianartificer.townstead.client.species.RigModels;
import net.conczin.mca.MCAClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * For an alternate-rig player (skeletownie) we draw the rig's own first-person arm in
 * {@link com.aetherianartificer.townstead.mixin.client.PlayerFirstPersonRigMixin}; this stops MCA
 * from also drawing the host villager arm there. {@code MCAClient.renderArms} gates MCA's custom
 * first-person arm, so returning false for these players removes it deterministically (no priority
 * race with MCA's PlayerRenderer mixin).
 */
@Mixin(MCAClient.class)
public abstract class McaRenderArmsMixin {

    @Inject(method = "renderArms", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void townstead$skipForAltRig(UUID uuid, String key, CallbackInfoReturnable<Boolean> cir) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;
        Player player = level.getPlayerByUUID(uuid);
        if (player != null && RigModels.isAlternate(RigModels.rigBaseFor(player))) cir.setReturnValue(false);
    }
}
