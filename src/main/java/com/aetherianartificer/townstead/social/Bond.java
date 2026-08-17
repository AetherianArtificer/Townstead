package com.aetherianartificer.townstead.social;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A tie between two people. Marriage is the first kind; the vocabulary is open
 * so apprenticeships, rivalries, debts and oaths can join it without a new
 * concept each time.
 *
 * <p>{@code endDay} of {@link #ONGOING} means the bond still holds. A bond that
 * ended is kept rather than erased, because "widowed" and "never married" are
 * different states and stories care about the difference.</p>
 */
public record Bond(String kind, @Nullable UUID other, String otherName,
                   long startDay, long endDay) {

    public static final long ONGOING = -1L;

    public Bond {
        kind = kind == null ? "" : kind;
        otherName = otherName == null ? "" : otherName;
    }

    public static Bond ongoing(String kind, @Nullable UUID other, String otherName, long startDay) {
        return new Bond(kind, other, otherName, startDay, ONGOING);
    }

    public boolean active() {
        return endDay == ONGOING;
    }

    public Bond ended(long day) {
        return new Bond(kind, other, otherName, startDay, day);
    }
}
