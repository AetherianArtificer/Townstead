package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.calendar.types.EclipticMath;
import com.aetherianartificer.townstead.calendar.types.LocaltimeMath;
import com.aetherianartificer.townstead.calendar.types.RealtimeMath;
import com.aetherianartificer.townstead.calendar.types.SereneMath;
import com.aetherianartificer.townstead.calendar.types.TfcMath;
import com.aetherianartificer.townstead.calendar.types.VanillaMath;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Java-side registry of {@link CalendarType} implementations. Profiles
 * reference these by id. Unlike {@link CalendarProfileRegistry} this is fixed
 * at class-load time, never data-pack-driven (compute strategies are code).
 */
public final class CalendarTypes {
    private static final Map<ResourceLocation, CalendarType> TYPES = new LinkedHashMap<>();

    static {
        register(new VanillaMath());
        register(new RealtimeMath());
        register(new LocaltimeMath());
        register(new SereneMath());
        register(new TfcMath());
        register(new EclipticMath());
    }

    private CalendarTypes() {}

    private static void register(CalendarType type) {
        TYPES.put(type.id(), type);
    }

    @Nullable
    public static CalendarType byId(ResourceLocation id) {
        return TYPES.get(id);
    }
}
