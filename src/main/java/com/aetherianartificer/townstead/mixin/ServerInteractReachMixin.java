package com.aetherianartificer.townstead.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
//? if neoforge {
import com.aetherianartificer.townstead.root.attachment.HitShapes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.injection.At;
//?}

/**
 * Vanilla checks a swing's reach against the target's body, so a long tail's tip could be in your sights and
 * out of reach. The server measures against the pose it works out instead. Forge's own check on 1.20.1 is
 * already generous enough to cover this.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerInteractReachMixin {

    //? if neoforge {
    @WrapOperation(method = "handleInteract", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getBoundingBox()Lnet/minecraft/world/phys/AABB;"))
    private AABB townstead$reachToAttachments(Entity target, Operation<AABB> original) {
        return HitShapes.reachBounds(target, original.call(target));
    }
    //?}
}
