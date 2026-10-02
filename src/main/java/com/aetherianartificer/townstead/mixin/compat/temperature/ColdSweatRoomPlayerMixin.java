package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.world.entity.LivingEntity;
import java.util.List;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.common.capability.temperature.AbstractTempCap")
public class ColdSweatRoomPlayerMixin {
    @Inject(method = "modifyFromAttribute(Lnet/minecraft/world/entity/LivingEntity;Lcom/momosoftworks/coldsweat/api/util/Temperature$Trait;Ljava/util/List;D)D",
            at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$room(LivingEntity entity, @Coerce Object trait, List<?> modifiers, double base,
                               CallbackInfoReturnable<Double> cir) {
        if (trait instanceof Enum<?> value && value.name().equals("WORLD"))
            cir.setReturnValue(RoomHeatBackend.coldSweatPlayer(entity.level(), entity.blockPosition(), cir.getReturnValueD()));
    }
}
