package com.aetherianartificer.townstead.mixin.compat.temperature;
import com.aetherianartificer.townstead.compat.temperature.RoomHeatBackend;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "sfiomn.legendarysurvivaloverhaul.util.internal.TemperatureUtilInternal")
public class LsoRoomQueryMixin {
    @Inject(method = "getWorldTemperature", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void townstead$room(Level level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        var room = RoomHeatBackend.room(level, pos);
        if (room.isPresent()) cir.setReturnValue((float) room.getAsDouble());
    }
    // Replace the environmental sum before LSO applies player effects and dynamic resistance.
    @Redirect(method = "getPlayerTargetTemperature", at = @At(value = "INVOKE",
            target = "Lsfiomn/legendarysurvivaloverhaul/api/temperature/ModifierBase;getWorldInfluence(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)F"), remap = false, require = 0)
    private float townstead$environment(@Coerce Object modifier, Player player, Level level, BlockPos pos) {
        var room = RoomHeatBackend.room(level, pos);
        if (room.isPresent()) return modifier.getClass().getSimpleName().equals("BiomeModifier") ? (float) room.getAsDouble() : 0f;
        try {
            return ((Number) modifier.getClass().getMethod("getWorldInfluence", Player.class, Level.class, BlockPos.class)
                    .invoke(modifier, player, level, pos)).floatValue();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("LSO environmental query failed", e); }
    }
}
