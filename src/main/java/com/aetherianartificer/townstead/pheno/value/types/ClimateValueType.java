package com.aetherianartificer.townstead.pheno.value.types;

import com.aetherianartificer.townstead.calendar.SeasonClock;
import com.aetherianartificer.townstead.climate.ClimateModel;
import com.aetherianartificer.townstead.climate.ClimateZone;
import com.aetherianartificer.townstead.climate.ClimateZones;
import com.aetherianartificer.townstead.pheno.value.Value;
import com.aetherianartificer.townstead.pheno.value.ValueType;
import com.aetherianartificer.townstead.temperature.GroundClimate;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;

import java.util.Locale;
import java.util.function.ToDoubleFunction;

/**
 * One part of the climate at the focus. {@code field} is one of {@code mean} (the day's mean in
 * Celsius), {@code seasonal} (today's seasonal offset), {@code diurnal} (the day/night offset now),
 * {@code phase} (0 to 1 from the start of spring; 0 without a season mod), {@code swing},
 * {@code humidity}, {@code diurnal_range} (the zone's values; 0 without a zone) or {@code depth}
 * (blocks of terrain overhead).
 *
 * <p>JSON: {@code { "type":"pheno:climate", "field":"humidity" }}</p>
 */
public final class ClimateValueType implements ValueType {
    public static final String KEY = "pheno:climate";

    private record Focus(ServerLevel level, BlockPos pos) {}

    @Override public String key() { return KEY; }

    @Override public Value parse(JsonObject json) {
        String field = GsonHelper.getAsString(json, "field", "mean").toLowerCase(Locale.ROOT);
        ToDoubleFunction<Focus> read = switch (field) {
            case "mean" -> f -> ClimateModel.dailyMean(f.level(), f.pos(), 0);
            case "seasonal" -> f -> ClimateModel.sample(f.level(), f.pos()).seasonal();
            case "diurnal" -> f -> ClimateModel.sample(f.level(), f.pos()).diurnal();
            case "phase" -> f -> SeasonClock.current(f.level().getServer()).map(SeasonClock.State::phase).orElse(0.0);
            case "swing" -> f -> zone(f).map(ClimateZone::seasonalSwing).orElse(0f);
            case "humidity" -> f -> zone(f).map(ClimateZone::humidity).orElse(0f);
            case "diurnal_range" -> f -> zone(f).map(ClimateZone::diurnalRange).orElse(0f);
            case "depth" -> f -> GroundClimate.probe(f.level(), f.pos()).depth();
            default -> f -> 0;
        };
        return ctx -> ctx.level() instanceof ServerLevel level ? read.applyAsDouble(new Focus(level, ctx.focusBlock())) : 0;
    }

    private static java.util.Optional<ClimateZone> zone(Focus focus) {
        return ClimateZones.of(focus.level().getBiome(focus.pos()));
    }
}
