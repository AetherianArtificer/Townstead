package com.aetherianartificer.townstead.mixin.compat.stardewhud;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import com.aetherianartificer.townstead.compat.stardewhud.StardewSeasonMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides Stardew HUD's hardcoded 4-cycle season math with Townstead's
 * authoritative season, when one is available. Stardew HUD's native logic is
 * {@code ((day - seasonStartOffset * seasonDays) / seasonDays) % 4} from
 * vanilla dayTime; that won't agree with Serene Seasons, TFC, Ecliptic, or
 * any future seasonal mod Townstead bridges.
 *
 * We only override when {@link CalendarClientStore} has a synced season
 * (i.e., the active profile is mod-bound). For non-seasonal profiles (such as
 * the default Gregorian profile), {@code hasSeason()} is false and Stardew HUD's own cycle
 * continues to drive the icon — preserving its behavior for players who
 * intentionally chose a non-seasonal profile.
 *
 * Stardew HUD is NeoForge 1.21.1 only; on Forge 1.20.1 builds the @Pseudo
 * mixin silently no-ops via {@code require = 0}.
 */
@Pseudo
@Mixin(targets = "wb.stardewhud.hud.components.SeasonComponent")
public class StardewHudSeasonMixin {

    @Inject(method = "calculateSeasonIndex", at = @At("HEAD"),
            cancellable = true, remap = false, require = 0)
    private void townstead$overrideSeasonIndex(long day, int seasonDays,
                                                CallbackInfoReturnable<Integer> cir) {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null || !snap.hasSeason()) return;
        int idx = StardewSeasonMapping.indexFromKey(snap.seasonKey());
        if (idx >= 0) cir.setReturnValue(idx);
    }
}
