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
 * Priority: TFC > Serene Seasons > Ecliptic Seasons > Gregorian (the default,
 * non-seasonal fallback).
 */
public final class CalendarCompat {
    public static final String SERENE_MOD_ID = "sereneseasons";
    public static final String TFC_MOD_ID = "terrafirmacraft";
    public static final String ECLIPTIC_MOD_ID = "eclipticseasons";

    /**
     * Namespace housing Townstead's bundled calendar data + lang strings.
     * Separate from the {@code townstead} mod namespace so calendar additions
     * (new profiles, translations) don't clash with the rest of the mod's
     * registries — modpack authors adding their own calendars use their own
     * namespace, and translation packs override only what they target.
     */
    public static final String CALENDAR_NAMESPACE = "townstead_calendar";

    //? if >=1.21 {
    private static final ResourceLocation VANILLA_ID = ResourceLocation.fromNamespaceAndPath(CALENDAR_NAMESPACE, "default");
    private static final ResourceLocation SERENE_ID = ResourceLocation.fromNamespaceAndPath(CALENDAR_NAMESPACE, "serene");
    private static final ResourceLocation TFC_ID = ResourceLocation.fromNamespaceAndPath(CALENDAR_NAMESPACE, "tfc");
    private static final ResourceLocation ECLIPTIC_ID = ResourceLocation.fromNamespaceAndPath(CALENDAR_NAMESPACE, "ecliptic");
    //?} else {
    /*private static final ResourceLocation VANILLA_ID = new ResourceLocation(CALENDAR_NAMESPACE, "default");
    private static final ResourceLocation SERENE_ID = new ResourceLocation(CALENDAR_NAMESPACE, "serene");
    private static final ResourceLocation TFC_ID = new ResourceLocation(CALENDAR_NAMESPACE, "tfc");
    private static final ResourceLocation ECLIPTIC_ID = new ResourceLocation(CALENDAR_NAMESPACE, "ecliptic");
    *///?}

    private CalendarCompat() {}

    /**
     * Auto-detection priority: TFC > Serene > Ecliptic > Default.
     *
     * Seasonal mods rank above Default because they bring authoritative
     * seasons (and Townstead's Serene/TFC/Ecliptic profiles already match
     * their day-length conventions).
     */
    public static ResourceLocation resolveAutoId() {
        if (ModCompat.isLoaded(TFC_MOD_ID)) return TFC_ID;
        if (ModCompat.isLoaded(SERENE_MOD_ID)) return SERENE_ID;
        if (ModCompat.isLoaded(ECLIPTIC_MOD_ID)) return ECLIPTIC_ID;
        return VANILLA_ID;
    }

    /**
     * True when any seasonal partner mod (TFC, Serene Seasons, Ecliptic
     * Seasons) is present. These mods derive their season from world
     * {@code dayTime}, so the fresh-world date randomization is suppressed when
     * one is installed: the calendar starts at day 0 (the beginning of the
     * seasonal cycle) instead of a random day-of-year that would land the world
     * in a random season.
     */
    public static boolean isSeasonalModLoaded() {
        return ModCompat.isLoaded(TFC_MOD_ID)
                || ModCompat.isLoaded(SERENE_MOD_ID)
                || ModCompat.isLoaded(ECLIPTIC_MOD_ID);
    }

    public static ResourceLocation vanillaId() { return VANILLA_ID; }
    public static ResourceLocation sereneId() { return SERENE_ID; }
    public static ResourceLocation tfcId() { return TFC_ID; }
    public static ResourceLocation eclipticId() { return ECLIPTIC_ID; }
}
