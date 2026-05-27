package com.aetherianartificer.townstead.mixin.compat.stardewhud;

import com.aetherianartificer.townstead.calendar.CalendarClientStore;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Aligns Stardew HUD's date line with Townstead's calendar. Stardew HUD has no
 * concept of months: its {@code currentDay} is the absolute Minecraft day
 * counter {@code floor(getDayTime() / 24000) + gameDayStartOffset}, rendered as
 * {@code "D %d %s"} (day number + short weekday). That number never agrees with
 * Townstead's day-of-month once a non-trivial profile (Gregorian, leap rules)
 * or a {@code time_mode} world-day offset is in play.
 *
 * When {@link CalendarClientStore} has a synced calendar we overwrite both
 * fields with Townstead's authoritative day-of-month and resolved short
 * weekday name. Stardew HUD wraps {@code currentWeekdayKey} in
 * {@code Component.translatable(...).getString()}; an already-resolved literal
 * passes through unchanged, so its fixed 7-day Mon..Sun set never gets in the
 * way and arbitrary week lengths render their own weekday name.
 *
 * Stardew HUD is NeoForge 1.21.1 only; on Forge 1.20.1 builds the @Pseudo
 * mixin silently no-ops via {@code require = 0}.
 */
@Pseudo
@Mixin(targets = "wb.stardewhud.hud.components.TimeDisplayComponent", remap = false)
public abstract class StardewHudDateMixin {

    @Shadow private long currentDay;
    @Shadow private String currentWeekdayKey;

    @Inject(method = "update", at = @At("TAIL"), require = 0)
    private void townstead$overrideDate(Level level, CallbackInfo ci) {
        townstead$applyTownsteadDate();
    }

    @Inject(method = "syncWithWorldTime", at = @At("TAIL"), require = 0)
    private void townstead$overrideDateOnSync(Level level, CallbackInfo ci) {
        townstead$applyTownsteadDate();
    }

    @Unique
    private void townstead$applyTownsteadDate() {
        CalendarClientStore.Snapshot snap = CalendarClientStore.get();
        if (snap == null) return;
        this.currentDay = snap.dayOfMonth();
        int dow = snap.dayOfWeek();
        if (snap.hasWeekdays() && dow >= 0 && dow < snap.weekdays().size()) {
            this.currentWeekdayKey = snap.weekdays().get(dow).shortComponent().getString();
        }
    }
}
