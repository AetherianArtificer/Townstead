package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "toughasnails.temperature.TemperatureHelperImpl")
public class TanRoomQueryMixin {
    @Inject(method = "getTemperatureAtPos", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$room(Level level, BlockPos pos, CallbackInfoReturnable<Object> cir) {
        var room = RoomHeatBackend.room(level, pos);
        if (room.isPresent() && cir.getReturnValue() instanceof Enum<?> original) {
            String name = new String[]{"ICY", "COLD", "NEUTRAL", "WARM", "HOT"}[RoomHeatBackend.tanOrdinal(room.getAsDouble())];
            for (Object value : original.getDeclaringClass().getEnumConstants())
                if (((Enum<?>) value).name().equals(name)) { cir.setReturnValue(value); break; }
        }
    }
}
