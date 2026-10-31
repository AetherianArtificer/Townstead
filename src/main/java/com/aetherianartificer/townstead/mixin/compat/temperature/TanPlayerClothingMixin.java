package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.temperature.PlayerFeelsLike;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** While Townstead decides what players feel, their clothing and wetness reach Tough As Nails through it, once. */
@Pseudo
@Mixin(targets = "toughasnails.temperature.TemperatureHelperImpl", remap = false)
public class TanPlayerClothingMixin {
    @Inject(method = "armorModifier", at = @At("HEAD"), cancellable = true, require = 0)
    private static void townstead$clothing(Player player, @Coerce Object current, CallbackInfoReturnable<Object> cir) {
        if (PlayerFeelsLike.active()) cir.setReturnValue(current);
    }

    @Inject(method = "immersionModifier", at = @At("HEAD"), cancellable = true, require = 0)
    private static void townstead$wetness(Player player, @Coerce Object current, CallbackInfoReturnable<Object> cir) {
        if (PlayerFeelsLike.active()) cir.setReturnValue(current);
    }
}
