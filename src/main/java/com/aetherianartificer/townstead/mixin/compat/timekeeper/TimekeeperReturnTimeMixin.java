package com.aetherianartificer.townstead.mixin.compat.timekeeper;

import com.aetherianartificer.townstead.compat.calendar.timekeeper.TimekeeperDateFormat;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Replaces Timekeeper's vanilla-calendar-block tooltip text with Townstead's
 * current date. Hooks only {@code ReturnVanillaTimeProcedure.execute} (called
 * from {@code CalendarVanillaBlock.appendHoverText}). The real / serene / tfc
 * calendar block variants are left untouched so users who want their native
 * Timekeeper behavior still get it.
 *
 * Timekeeper ships both Forge 1.20.1 and NeoForge 1.21.1 with identical
 * procedure class names and signatures; {@code execute} is a Timekeeper
 * method (not vanilla) so {@code remap = false} works on both branches.
 */
@Pseudo
@Mixin(targets = "net.lunakibby.timekeeper.procedures.ReturnVanillaTimeProcedure")
public class TimekeeperReturnTimeMixin {

    @Inject(method = "execute(Lnet/minecraft/world/level/LevelAccessor;)Ljava/lang/String;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void townstead$returnTownsteadDate(LevelAccessor level, CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(TimekeeperDateFormat.tooltipString());
    }
}
