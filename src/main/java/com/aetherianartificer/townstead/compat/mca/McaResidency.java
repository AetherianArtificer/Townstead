package com.aetherianartificer.townstead.compat.mca;

import net.conczin.mca.entity.VillagerEntityMCA;

/**
 * Village membership calls across MCA lines. The 1.21.1 line renamed {@code seekHome} to
 * {@code reconcileVillageMembership} and {@code leaveHome} to {@code leaveVillage}; both
 * pairs behave the same, and neither leave call touches HOME or its POI ticket.
 */
public final class McaResidency {
    private McaResidency() {}

    /** Joins the closest village in range and indexes the current HOME. */
    public static void joinVillage(VillagerEntityMCA villager) {
        //? if >=1.21 {
        villager.getResidency().reconcileVillageMembership();
        //?} else {
        /*villager.getResidency().seekHome();
        *///?}
    }

    /** Removes village membership only. */
    public static void leaveVillage(VillagerEntityMCA villager) {
        //? if >=1.21 {
        villager.getResidency().leaveVillage();
        //?} else {
        /*villager.getResidency().leaveHome();
        *///?}
    }
}
