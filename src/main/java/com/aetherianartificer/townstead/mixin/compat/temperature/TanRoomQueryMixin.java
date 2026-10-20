package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "toughasnails.temperature.TemperatureHelperImpl")
public class TanRoomQueryMixin {
    @Inject(method = "getTemperatureAtPos", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
    private void townstead$room(Level level, BlockPos pos, CallbackInfoReturnable<Object> cir) {
        if (!(cir.getReturnValue() instanceof Enum<?> original)) return;
        var room = RoomHeatBackend.room(level, pos);
        String name = null;
        if (room.isPresent()) name = new String[]{"ICY", "COLD", "NEUTRAL", "WARM", "HOT"}[RoomHeatBackend.tanOrdinal(room.getAsDouble())];
        else if (!RoomHeatBackend.bypassed() && level instanceof ServerLevel server) name = RoomHeatBackend.tanOutdoor(server, pos, original.name());
        if (name == null) return;
        for (Object value : original.getDeclaringClass().getEnumConstants())
            if (((Enum<?>) value).name().equals(name)) { cir.setReturnValue(value); break; }
    }
}
