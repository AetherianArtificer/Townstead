package com.aetherianartificer.townstead.calendar;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One leap rule on a calendar profile. Composed of a {@link Predicate} that
 * decides whether the rule fires in a given year, and an {@link Action} that
 * mutates the year's month list when it fires.
 *
 * Rules evaluate top-to-bottom and all matching rules apply (Gregorian's
 * {@code 4/100/400} cascade is three rules in order — every rule that
 * matches contributes a +1 / -1 / +1 day adjustment).
 *
 * <p>The model is intentionally tiny:</p>
 * <ul>
 *   <li>Predicates are restricted to modular arithmetic on the year number.
 *       No expressions, no scripting. Every real-world deterministic leap
 *       rule plus essentially any fantasy "every N-th year" rule reduces to
 *       this form.</li>
 *   <li>Actions are either {@code +1} / {@code -1} days on an existing month
 *       (referenced by 1-based index into the base month list) or a whole
 *       inserted month (after a base month index, or appended).</li>
 *   <li>Indices in actions refer to the <em>base</em> profile month list, not
 *       the post-mutation list. Rules don't compose across iterations.</li>
 * </ul>
 *
 * <p>Observation-based calendars (Hijri lunar) and rules that depend on
 * astronomical events are out of scope — those need server-side observation
 * data, not a static profile.</p>
 */
public record LeapRule(Predicate when, Action action) {

    public LeapRule {
        if (when == null) throw new IllegalArgumentException("LeapRule.when must not be null");
        if (action == null) throw new IllegalArgumentException("LeapRule.action must not be null");
    }

    // ---------- Predicate ----------

    /**
     * Decides whether a rule fires in a given absolute year. Implementations
     * are pure functions of the year number.
     */
    public interface Predicate {
        boolean test(int year);
    }

    /** {@code year % mod == equals}. */
    public record Equals(int mod, int equalsValue) implements Predicate {
        public Equals {
            if (mod <= 0) throw new IllegalArgumentException("year_mod must be > 0");
        }
        @Override
        public boolean test(int year) {
            return Math.floorMod(year, mod) == Math.floorMod(equalsValue, mod);
        }
    }

    /** {@code year % mod} is in a fixed set of remainders. */
    public record In(int mod, int[] residues) implements Predicate {
        public In {
            if (mod <= 0) throw new IllegalArgumentException("year_mod must be > 0");
            if (residues == null || residues.length == 0)
                throw new IllegalArgumentException("In.residues must not be empty");
            residues = residues.clone();
        }
        @Override
        public boolean test(int year) {
            int r = Math.floorMod(year, mod);
            for (int v : residues) if (Math.floorMod(v, mod) == r) return true;
            return false;
        }
    }

    /** All sub-predicates must match. */
    public record AllOf(List<Predicate> parts) implements Predicate {
        public AllOf {
            if (parts == null || parts.isEmpty())
                throw new IllegalArgumentException("AllOf.parts must not be empty");
            parts = List.copyOf(parts);
        }
        @Override
        public boolean test(int year) {
            for (Predicate p : parts) if (!p.test(year)) return false;
            return true;
        }
    }

    /** At least one sub-predicate must match. */
    public record AnyOf(List<Predicate> parts) implements Predicate {
        public AnyOf {
            if (parts == null || parts.isEmpty())
                throw new IllegalArgumentException("AnyOf.parts must not be empty");
            parts = List.copyOf(parts);
        }
        @Override
        public boolean test(int year) {
            for (Predicate p : parts) if (p.test(year)) return true;
            return false;
        }
    }

    // ---------- Action ----------

    /**
     * Mutates the per-year month list when the parent rule fires. The
     * applier in {@link LeapEngine} walks a working copy of the base month
     * list, applying each matching rule's action in order.
     */
    public interface Action {}

    /**
     * Add {@code delta} days to the month at 1-based {@code monthIndex} in
     * the base profile month list. Negative delta subtracts (rules use a
     * separate JSON key {@code subtract_day_from_month} but it's just
     * {@code delta = -1} under the hood).
     */
    public record AdjustDays(int monthIndex, int delta) implements Action {
        public AdjustDays {
            if (monthIndex < 1) throw new IllegalArgumentException("monthIndex must be >= 1");
            if (delta == 0) throw new IllegalArgumentException("delta must not be 0");
        }
    }

    /**
     * Insert an entire month either after the given 1-based base monthIndex,
     * or appended at the end of the year (when {@code afterMonthIndex} is
     * null). The inserted {@link MonthDef} carries its display name + day
     * count just like a base month.
     */
    public record InsertMonth(@Nullable Integer afterMonthIndex, MonthDef month) implements Action {
        public InsertMonth {
            if (month == null) throw new IllegalArgumentException("InsertMonth.month must not be null");
            if (afterMonthIndex != null && afterMonthIndex < 0)
                throw new IllegalArgumentException("afterMonthIndex must be >= 0 (0 = before first)");
        }
    }

    /**
     * Override the display name of the month at 1-based {@code monthIndex}
     * (into the base month list) for years where the parent predicate fires.
     * Day count is unchanged.
     *
     * <p>Typical use: a leap rule that {@code insert_month_after}s "Adar I"
     * also renames the base "Adar" to "Adar II" so Hebrew leap-year output
     * matches tradition. Equally useful for fantasy calendars where a month
     * picks up an epithet in special years ("Sunmoon" → "Blood Sunmoon"
     * every 13th year) without inserting anything.</p>
     */
    public record RenameMonth(int monthIndex, Component newName) implements Action {
        public RenameMonth {
            if (monthIndex < 1) throw new IllegalArgumentException("monthIndex must be >= 1");
            if (newName == null) throw new IllegalArgumentException("RenameMonth.newName must not be null");
        }
    }
}
