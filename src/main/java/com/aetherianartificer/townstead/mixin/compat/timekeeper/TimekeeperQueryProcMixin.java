package com.aetherianartificer.townstead.mixin.compat.timekeeper;

import com.aetherianartificer.townstead.compat.calendar.timekeeper.TimekeeperDateFormat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces Timekeeper's vanilla-calendar-block right-click chat message with
 * Townstead's current date. Hooks only the two vanilla query procedures
 * ({@code QueryProcDayProcedure} short form, {@code QueryProcDayFullProcedure}
 * full form) that {@code CalendarVanillaBlock.useWithoutItem} invokes. The
 * real / serene / tfc query procedures are left untouched.
 *
 * Runs server-side only; the original procedures gate on
 * {@code !level.isClientSide()} so we do the same to avoid double-display.
 *
 * Timekeeper ships on both Forge 1.20.1 and NeoForge 1.21.1; this mixin
 * targets the same class names and method signatures on both.
 */
@Pseudo
@Mixin(targets = {
        "net.lunakibby.timekeeper.procedures.QueryProcDayProcedure",
        "net.lunakibby.timekeeper.procedures.QueryProcDayFullProcedure"
})
public class TimekeeperQueryProcMixin {

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/Entity;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void townstead$sendTownsteadDate(LevelAccessor level, Entity actor, CallbackInfo ci) {
        if (!(actor instanceof Player player)) return;
        if (player.level().isClientSide()) {
            ci.cancel();
            return;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        player.displayClientMessage(TimekeeperDateFormat.chatComponent(server), true);
        ci.cancel();
    }
}
