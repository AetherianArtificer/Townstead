package com.aetherianartificer.townstead.calendar.types;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.calendar.CalendarDate;
import com.aetherianartificer.townstead.calendar.CalendarProfile;
import com.aetherianartificer.townstead.calendar.CalendarType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Same month math as {@link VanillaMath} but the day advances when the host's
 * system clock crosses midnight, not when in-game time does. Pairs with mods
 * that stretch one MC day across many real-world days.
 */
public class RealtimeMath implements CalendarType {
    //? if >=1.21 {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "realtime_math");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "realtime_math");
    *///?}

    private static final long MILLIS_PER_DAY = 86_400_000L;
    private final VanillaMath delegate = new VanillaMath();

    @Override
    public ResourceLocation id() { return ID; }

    @Override
    public CalendarDate compute(MinecraftServer server, CalendarProfile profile, long worldDay, int epochYearOffset) {
        long systemDays = System.currentTimeMillis() / MILLIS_PER_DAY;
        return delegate.compute(server, profile, systemDays, epochYearOffset);
    }
}
