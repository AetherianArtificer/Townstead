package com.aetherianartificer.townstead.mixin;

import com.aetherianartificer.townstead.root.trigger.GeneTriggers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Non-player transfers return the restored destination entity; players use the loader's post event. */
@Mixin(Entity.class)
public abstract class EntityDimensionEntryMixin {
    @Unique private ResourceKey<Level> townstead$departureDimension;

    //? if neoforge {
    @Inject(method = "changeDimension", at = @At("HEAD"))
    //?} else {
    /*@Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;", at = @At("HEAD"), remap = false)
    *///?}
    private void townstead$rememberDeparture(CallbackInfoReturnable<Entity> cir) {
        townstead$departureDimension = ((Entity) (Object) this).level().dimension();
    }

    //? if neoforge {
    @Inject(method = "changeDimension", at = @At("RETURN"))
    //?} else {
    /*@Inject(method = "changeDimension(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraftforge/common/util/ITeleporter;)Lnet/minecraft/world/entity/Entity;", at = @At("RETURN"), remap = false)
    *///?}
    private void townstead$dimensionEntered(CallbackInfoReturnable<Entity> cir) {
        if (cir.getReturnValue() instanceof LivingEntity living && !(living instanceof Player)
                && townstead$departureDimension != null) {
            GeneTriggers.onEnterDimension(living, townstead$departureDimension);
        }
    }
}
