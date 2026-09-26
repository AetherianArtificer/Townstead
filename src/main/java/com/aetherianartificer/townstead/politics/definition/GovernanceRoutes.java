package com.aetherianartificer.townstead.politics.definition;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The routes to power Townstead implements. Data can only name a route listed here, so a form
 * never claims a mechanic that does not exist; each later phase registers more.
 */
public final class GovernanceRoutes {
    public static final int DEFAULT_LEGITIMACY = 60;

    /** Villagers or members earn a council seat, then the council votes. */
    public static final ResourceLocation COUNCIL_VOTE = id("council_vote");
    /** A majority of residents hold the candidate in good standing. */
    public static final ResourceLocation ACCLAMATION = id("acclamation");
    /** The head names officers and an heir from people they trust. */
    public static final ResourceLocation FAVOR = id("favor");
    /** The named heir takes the office when it empties. */
    public static final ResourceLocation INHERITANCE = id("inheritance");

    private static final Set<ResourceLocation> KNOWN = new LinkedHashSet<>();

    static {
        register(COUNCIL_VOTE);
        register(ACCLAMATION);
        register(FAVOR);
        register(INHERITANCE);
    }

    private GovernanceRoutes() {}

    public static synchronized void register(ResourceLocation route) {
        KNOWN.add(route);
    }

    public static synchronized boolean isKnown(ResourceLocation route) {
        return KNOWN.contains(route);
    }

    public static synchronized Set<ResourceLocation> known() {
        return Set.copyOf(KNOWN);
    }

    private static ResourceLocation id(String path) {
        //? if >=1.21 {
        return ResourceLocation.fromNamespaceAndPath("townstead", path);
        //?} else {
        /*return new ResourceLocation("townstead", path);
        *///?}
    }
}
