package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.calendar.types.EclipticMath;
import com.aetherianartificer.townstead.calendar.types.SereneMath;
import com.aetherianartificer.townstead.calendar.types.TfcMath;
import com.aetherianartificer.townstead.calendar.types.VanillaMath;
import com.aetherianartificer.townstead.compat.calendar.CalendarCompat;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java-side registry of {@link CalendarType} implementations. Compute
 * strategies live in code, never data-pack-driven.
 *
 * <p><b>Driver resolution.</b> Profiles don't declare their math driver;
 * see {@link #resolveDriverFor}. Three seasonal-bridge profile ids
 * ({@code townstead_calendar:serene}, {@code :tfc}, {@code :ecliptic})
 * are hard-coded here because their driver is intrinsic to the partner mod.
 * Every other profile uses {@link VanillaMath} — the calendar tracks
 * {@link WorldCalendarSavedData#worldDayCounter}, which already rides
 * whatever MC time source is active (vanilla, Time Control, day-stretching
 * mods, etc.). Wall-clock pacing is achieved by changing how MC's day-night
 * cycle advances, not by swapping the calendar driver.</p>
 */
public final class CalendarTypes {
    private static final Map<ResourceLocation, CalendarType> TYPES = new LinkedHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> SEASONAL_BRIDGE_DRIVERS = new LinkedHashMap<>();

    static {
        register(new VanillaMath());
        register(new SereneMath());
        register(new TfcMath());
        register(new EclipticMath());

        SEASONAL_BRIDGE_DRIVERS.put(CalendarCompat.sereneId(),   SereneMath.ID);
        SEASONAL_BRIDGE_DRIVERS.put(CalendarCompat.tfcId(),      TfcMath.ID);
        SEASONAL_BRIDGE_DRIVERS.put(CalendarCompat.eclipticId(), EclipticMath.ID);
    }

    private CalendarTypes() {}

    private static void register(CalendarType type) {
        TYPES.put(type.id(), type);
    }

    @Nullable
    public static CalendarType byId(ResourceLocation id) {
        return TYPES.get(id);
    }

    /**
     * Pick the math driver for the given profile id. Seasonal-bridge profiles
     * return their hard-coded driver. Everything else uses {@link VanillaMath},
     * which advances the calendar with Townstead's monotonic
     * {@code worldDayCounter} (compatible with vanilla MC time and any
     * day-cycle mod).
     */
    public static CalendarType resolveDriverFor(@Nullable ResourceLocation profileId) {
        if (profileId != null) {
            ResourceLocation seasonal = SEASONAL_BRIDGE_DRIVERS.get(profileId);
            if (seasonal != null) {
                CalendarType t = TYPES.get(seasonal);
                if (t != null) return t;
            }
        }
        return TYPES.get(VanillaMath.ID);
    }
}
