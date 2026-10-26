package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import com.aetherianartificer.townstead.compat.temperature.LsoTemperatureBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The Serene API stub makes LSO run its Serene and its Ecliptic seasons together, counting every
 * season twice. With the stub present and LSO's Ecliptic seasons on, the Serene copy steps aside.
 */
@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.common.integration.sereneseasons.SereneSeasonsModifier")
public class LsoSereneUnderBridgeMixin {
    @Inject(method = "getWorldInfluence", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$oneSeasonCurve(Player player, Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (CalendarCompat.isSereneApiStub() && LsoTemperatureBridge.seasonFlag("eclipticSeasonsEnabled")) cir.setReturnValue(0f);
    }
}
