package com.aetherianartificer.townstead.chronicle.model;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A ground-truth event in the chronicle archive. Immutable once recorded.
 *
 * <p>Truth firewall: mechanical systems (counters, XP, unlocks) read events;
 * subjective systems (mood, sentiment) read accounts, never this directly.</p>
 *
 * <p>{@code worldDay} is the monotonic calendar day counter; values before a
 * village's fabricated founding are pre-history. {@code eventId} is 0 on a
 * draft and assigned by {@code Chronicles.record}.</p>
 */
public record ChronicleEvent(
        long eventId,
        ResourceLocation templateId,
        long worldDay,
        long gameTime,
        ResourceLocation dimension,
        long packedPos,
        int villageId,
        String category,
        float magnitude,
        int reach,
        long causeEventId,
        long arcId,
        boolean keep,
        List<Participation> participations,
        Map<String, String> params) {

    public static final int VILLAGE_NONE = -1;
    public static final long NONE = -1L;

    /** Reach classes: the structural ceiling on who may ever learn of this event. */
    public static final int REACH_NONE = 0;
    public static final int REACH_WITNESSES = 1;
    public static final int REACH_VILLAGE = 2;
    public static final int REACH_WORLD = 3;

    public ChronicleEvent {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(dimension, "dimension");
        category = category == null ? "" : category;
        participations = participations == null ? List.of() : List.copyOf(participations);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    public ChronicleEvent withId(long id) {
        return new ChronicleEvent(id, templateId, worldDay, gameTime, dimension, packedPos,
                villageId, category, magnitude, reach, causeEventId, arcId, keep,
                participations, params);
    }

    /** True when this event spreads (gets accounts) rather than only counting. */
    public boolean newsworthy() {
        return reach > REACH_NONE;
    }
}
