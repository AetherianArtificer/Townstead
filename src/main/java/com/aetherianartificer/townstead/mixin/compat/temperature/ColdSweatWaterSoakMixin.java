package com.aetherianartificer.townstead.mixin.compat.temperature;

import com.aetherianartificer.townstead.temperature.ThermalProfile;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/**
 * A root that stays dry carries no soak out of the water or in from the rain. In the water,
 * Cold Sweat still pulls it toward the water's temperature.
 */
@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.api.temperature.modifier.WaterTempModifier", remap = false)
public class ColdSweatWaterSoakMixin {
    @Inject(method = "calculate", at = @At("HEAD"), cancellable = true, require = 0)
    private void townstead$staysDry(LivingEntity entity, @Coerce Object trait,
                                    CallbackInfoReturnable<Function<Double, Double>> cir) {
        if (entity.isInWater() || !ThermalProfile.waterproof(entity)) return;
        try {
            Object self = this;
            self.getClass().getMethod("setTemperature", double.class).invoke(self, 0d);
            self.getClass().getMethod("expires", int.class).invoke(self, 0);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        cir.setReturnValue(temp -> temp);
    }
}
