package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Phase 1 stub. Delegates structure to {@link VanillaMath} but bakes in the
 * TFC "year 1000" convention on top of the admin's epoch offset, so a fresh
 * TFC world reads as year 1000 by default (matching player expectations).
 *
 * Phase 3 will replace this with a reflection probe of
 * {@code net.dries007.tfc.util.calendar.Calendars} for real TFC calendar
 * synchronization.
 */
public class TfcMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "tfc_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "tfc_math");
    *///?}

    private static final int TFC_BASE_YEAR = 1000;
    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        CalendarDate base = delegate.compute(server, profile, worldDay, epochYearOffset + TFC_BASE_YEAR);
        return base;
    }
}
