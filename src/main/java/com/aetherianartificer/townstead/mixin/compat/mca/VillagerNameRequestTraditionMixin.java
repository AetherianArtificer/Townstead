package com.aetherianartificer.townstead.mixin.compat.mca;

import com.aetherianartificer.townstead.root.PlayerRoot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.c2s.VillagerNameRequest;
import net.conczin.mca.resources.Names;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The editor's random name button draws from the player's naming tradition once they have one. */
@Mixin(value = VillagerNameRequest.class, remap = false)
public abstract class VillagerNameRequestTraditionMixin {

    //? if neoforge {
    @WrapOperation(method = "handleServer", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/resources/Names;pickCitizenName(Lnet/conczin/mca/entity/ai/relationship/Gender;)Ljava/lang/String;"), require = 0)
    //?} else {
    /*@WrapOperation(method = "receive", at = @At(value = "INVOKE",
            target = "Lnet/conczin/mca/resources/Names;pickCitizenName(Lnet/conczin/mca/entity/ai/relationship/Gender;)Ljava/lang/String;"), require = 0)
    *///?}
    private String townstead$pickFromTradition(Gender gender, Operation<String> original, ServerPlayer player) {
        return PlayerRoot.getNamingTradition(player).isEmpty()
                ? original.call(gender)
                : Names.pickCitizenName(gender, player);
    }
}
