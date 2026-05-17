package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Phase 1 stub. Currently delegates to {@link VanillaMath} so the bundled
 * Serene Seasons profile (96 days, 16 weeks of 6) renders correctly by
 * structure even before Phase 3 wires the real
 * {@code sereneseasons.api.season.SeasonHelper} bindings.
 *
 * When Phase 3 lands, this compute() will query the seasonal mod for
 * authoritative season state and stamp {@link CalendarDate#season}. Per
 * [[feedback_seasons_only_from_mods]], the season MUST come from the mod
 * at query time, never from Townstead's own data.
 */
public class SereneMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "serene_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "serene_math");
    *///?}

    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        return delegate.compute(server, profile, worldDay, epochYearOffset);
    }
}
