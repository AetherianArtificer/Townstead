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
        var room = RoomHeatBackend.environment(level, pos);
        if (room.isPresent()) cir.setReturnValue((float) room.getAsDouble());
    }
    /** Clothing, wetness, sprinting and resistance are already in what the player feels. */
    @Redirect(method = "getPlayerTargetTemperature", at = @At(value = "INVOKE",
            target = "Lsfiomn/legendarysurvivaloverhaul/api/temperature/ModifierBase;getPlayerInfluence(Lnet/minecraft/world/entity/player/Player;)F"), remap = false, require = 0)
    private float townstead$player(@Coerce Object modifier, Player player) {
        if (com.aetherianartificer.townstead.temperature.PlayerFeelsLike.active()) {
            switch (modifier.getClass().getSimpleName()) {
                case "WetModifier", "AttributeModifier", "SprintModifier" -> { return 0f; }
                default -> { }
            }
        }
        try {
            return ((Number) modifier.getClass().getMethod("getPlayerInfluence", Player.class).invoke(modifier, player)).floatValue();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("LSO player query failed", e); }
    }
    @Redirect(method = "getPlayerTargetTemperature", at = @At(value = "INVOKE",
            target = "Lsfiomn/legendarysurvivaloverhaul/api/temperature/DynamicModifierBase;applyDynamicPlayerInfluence(Lnet/minecraft/world/entity/player/Player;FF)F"), remap = false, require = 0)
    private float townstead$resistance(@Coerce Object modifier, Player player, float temperature, float resistance) {
        if (com.aetherianartificer.townstead.temperature.PlayerFeelsLike.active()
                && modifier.getClass().getSimpleName().equals("TemperatureResistanceModifier")) return 0f;
        try {
            return ((Number) modifier.getClass().getMethod("applyDynamicPlayerInfluence", Player.class, float.class, float.class)
                    .invoke(modifier, player, temperature, resistance)).floatValue();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("LSO resistance query failed", e); }
    }
    @Redirect(method = "getPlayerTargetTemperature", at = @At(value = "INVOKE",
            target = "Lsfiomn/legendarysurvivaloverhaul/api/temperature/DynamicModifierBase;applyDynamicWorldInfluence(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;FF)F"), remap = false, require = 0)
    private float townstead$worldResistance(@Coerce Object modifier, Player player, Level level, BlockPos pos, float temperature, float resistance) {
        if (com.aetherianartificer.townstead.temperature.PlayerFeelsLike.active()
                && modifier.getClass().getSimpleName().equals("TemperatureResistanceModifier")) return 0f;
        try {
            return ((Number) modifier.getClass().getMethod("applyDynamicWorldInfluence", Player.class, Level.class, BlockPos.class,
                    float.class, float.class).invoke(modifier, player, level, pos, temperature, resistance)).floatValue();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("LSO resistance query failed", e); }
    }
    // Replace the environmental sum before LSO applies player effects and dynamic resistance.
    @Redirect(method = "getPlayerTargetTemperature", at = @At(value = "INVOKE",
            target = "Lsfiomn/legendarysurvivaloverhaul/api/temperature/ModifierBase;getWorldInfluence(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)F"), remap = false, require = 0)
    private float townstead$environment(@Coerce Object modifier, Player player, Level level, BlockPos pos) {
        var room = RoomHeatBackend.environment(level, pos);
        String name = modifier.getClass().getSimpleName();
        boolean ownBlocks = !RoomHeatBackend.ownsBlockHeat() && name.equals("BlockModifier");
        if (room.isPresent() && !ownBlocks) {
            if (!name.equals("BiomeModifier")) return 0f;
            if (player == null || !com.aetherianartificer.townstead.temperature.PlayerFeelsLike.active()) return (float) room.getAsDouble();
            // What the player feels: clothing, wetness, activity, humidity and Root traits, on LSO's scale.
            float wet = com.aetherianartificer.townstead.compat.temperature.LsoTemperatureBridge.INSTANCE.playerWetness(player);
            return com.aetherianartificer.townstead.temperature.PlayerFeelsLike.sent(player, (float) room.getAsDouble(), wet);
        }
        try {
            return ((Number) modifier.getClass().getMethod("getWorldInfluence", Player.class, Level.class, BlockPos.class)
                    .invoke(modifier, player, level, pos)).floatValue();
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("LSO environmental query failed", e); }
    }
}
