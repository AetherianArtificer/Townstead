package com.aetherianartificer.townstead.compat.calendar;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.minecraft.resources.ResourceLocation;

/**
 * Maps detected seasonal mods to the bundled Townstead profile id that fits
 * them best. Profile *content* lives in data pack JSON
 * ({@code data/townstead/calendar_profile/*.json}); this class only does the
 * "which id to auto-resolve" decision when the config is set to {@code auto}.
 *
 * Priority: TFC > Serene Seasons > Ecliptic Seasons > Townstead Vanilla.
 * Realtime and Localtime are user-selected only — auto never picks them.
 */
public final class CalendarCompat {
    public static final String SERENE_MOD_ID = "sereneseasons";
    public static final String TFC_MOD_ID = "terrafirmacraft";
    public static final String ECLIPTIC_MOD_ID = "eclipticseasons";

    //? if >=1.21 {
    private static final ResourceLocation VANILLA_ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "vanilla");
    private static final ResourceLocation SERENE_ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "serene");
    private static final ResourceLocation TFC_ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "tfc");
    private static final ResourceLocation ECLIPTIC_ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "ecliptic");
    //?} else {
    /*private static final ResourceLocation VANILLA_ID = new ResourceLocation(Townstead.MOD_ID, "vanilla");
    private static final ResourceLocation SERENE_ID = new ResourceLocation(Townstead.MOD_ID, "serene");
    private static final ResourceLocation TFC_ID = new ResourceLocation(Townstead.MOD_ID, "tfc");
    private static final ResourceLocation ECLIPTIC_ID = new ResourceLocation(Townstead.MOD_ID, "ecliptic");
    *///?}

    private CalendarCompat() {}

    public static ResourceLocation resolveAutoId() {
        if (ModCompat.isLoaded(TFC_MOD_ID)) return TFC_ID;
        if (ModCompat.isLoaded(SERENE_MOD_ID)) return SERENE_ID;
        if (ModCompat.isLoaded(ECLIPTIC_MOD_ID)) return ECLIPTIC_ID;
        return VANILLA_ID;
    }

    public static ResourceLocation vanillaId() { return VANILLA_ID; }
}
