package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.momosoftworks.coldsweat.util.world.WorldHelper")
public class ColdSweatRoomQueryMixin {
    @Inject(method = "getTemperatureAt(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)D",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void townstead$room(Level level, BlockPos pos, CallbackInfoReturnable<Double> cir) {
        var room = RoomHeatBackend.room(level, pos);
        if (room.isPresent()) cir.setReturnValue(room.getAsDouble() / 25.0);
    }
}
