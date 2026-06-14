package com.aetherianartificer.townstead.origin.disposition;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The disposition seam. Sources are consulted newest-first, so a later-registered authoritative
 * source (the faction system, when it lands, with claims/alliances/reputation) overrides the natural
 * default for the same query. The first non-null answer wins. Today only
 * {@link NaturalDispositionSource} is registered, i.e. how things react in a world without factions.
 */
public final class Dispositions {

    private static final List<DispositionSource> SOURCES = new CopyOnWriteArrayList<>();

    private Dispositions() {}

    /** Register a source; the most recently registered is consulted first (it overrides earlier ones). */
    public static void register(DispositionSource source) {
        SOURCES.add(0, source);
    }

    /** How {@code viewer} regards {@code other} ({@link Disposition#NEUTRAL} if nothing has an opinion). */
    public static Disposition between(LivingEntity viewer, LivingEntity other) {
        if (viewer == null || other == null || viewer == other) return Disposition.NEUTRAL;
        for (DispositionSource source : SOURCES) {
            Disposition d = source.between(viewer, other);
            if (d != null) return d;
        }
        return Disposition.NEUTRAL;
    }

    public static boolean areFriendly(LivingEntity viewer, LivingEntity other) {
        return between(viewer, other) == Disposition.FRIENDLY;
    }
}
