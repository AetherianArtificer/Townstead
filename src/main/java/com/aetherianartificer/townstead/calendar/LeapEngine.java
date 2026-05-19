package com.aetherianartificer.townstead.calendar;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stateless evaluator for {@link LeapRule}s. Applies a profile's rule list to
 * a base month list to produce the year's effective month layout (which can
 * differ year-to-year if leap rules add or remove days, or insert months).
 *
 * <p>Used by both the server (via {@link CalendarProfile}) and the client
 * (via {@link CalendarClientStore.Snapshot}), so that historical date math is
 * identical on both sides.</p>
 *
 * <h2>Performance</h2>
 *
 * The fast path is "no leap rules" — every method short-circuits to a
 * constant lookup. With leap rules, results are memoized in a per-list-instance
 * cache keyed by year, so repeated queries for the same year (the common HUD
 * + sync case) cost a single map lookup.
 *
 * <p>The cache key is the identity of the base month list and the rule list.
 * For profile-driven use that's a stable identity (the {@link CalendarProfile}
 * record owns both lists). Client snapshots create new lists on each sync,
 * which is fine — the snapshot is also short-lived.</p>
 */
public final class LeapEngine {

    /**
     * Result of applying leap rules to a year. {@code months} may be the
     * base list unchanged, or a new list with adjusted day counts and/or
     * inserted months.
     */
    public record YearLayout(List<MonthDef> months, int daysPerYear) {
        public YearLayout {
            months = List.copyOf(months);
            if (daysPerYear < 0) throw new IllegalArgumentException("daysPerYear must be >= 0");
        }
    }

    // Per-(base months, rules) → (year → layout) cache. Bounded by usage
    // patterns; profiles are few and years queried are few.
    private static final ConcurrentMap<CacheKey, ConcurrentMap<Integer, YearLayout>> CACHE = new ConcurrentHashMap<>();

    private LeapEngine() {}

    /**
     * Build the layout for one year. If {@code leapRules} is null or empty,
     * returns a layout backed by the base month list with no year-dependent
     * mutation.
     */
    public static YearLayout layoutForYear(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules, int year) {
        if (leapRules == null || leapRules.isEmpty()) {
            return new YearLayout(baseMonths, sumDays(baseMonths));
        }
        CacheKey key = new CacheKey(baseMonths, leapRules);
        ConcurrentMap<Integer, YearLayout> yearMap = CACHE.computeIfAbsent(key, k -> new ConcurrentHashMap<>());
        YearLayout cached = yearMap.get(year);
        if (cached != null) return cached;
        YearLayout fresh = compute(baseMonths, leapRules, year);
        // ConcurrentHashMap.putIfAbsent semantics — if another thread beat us,
        // their layout is identical (same inputs, pure function).
        YearLayout prior = yearMap.putIfAbsent(year, fresh);
        return prior != null ? prior : fresh;
    }

    /**
     * Total days in {@code year}.
     */
    public static int daysInYear(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules, int year) {
        return layoutForYear(baseMonths, leapRules, year).daysPerYear();
    }

    /**
     * Days in the given 1-based month of {@code year}. Indexes into the
     * post-leap-rule month list — so for Hebrew leap years, monthIndex 13
     * is Adar II.
     */
    public static int daysInMonth(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules, int year, int monthIndex) {
        List<MonthDef> ms = layoutForYear(baseMonths, leapRules, year).months();
        if (monthIndex < 1 || monthIndex > ms.size()) return 0;
        return ms.get(monthIndex - 1).days();
    }

    /**
     * Split an absolute worldDay into a {@link Split} whose {@code year} is
     * the <em>display</em> year (absolute year-since-worldDay-0 plus the
     * supplied {@code epochYearOffset}). All predicate evaluations use the
     * display year so leap rules align with what the player sees in the
     * calendar UI.
     *
     * <p>Walks years forward from {@code epochYearOffset} (or backward for
     * negative worldDay), accumulating year lengths. Common case (no leap
     * rules) is one division. With leap rules the walk first hops in cycle
     * units when a modular cycle is detectable, then iterates within the
     * residual cycle — capped at the cycle period (≤ 400 for Gregorian, 19
     * for Hebrew, etc.).</p>
     */
    public static Split splitWorldDay(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules,
                                      long worldDay, int epochYearOffset) {
        if (leapRules == null || leapRules.isEmpty()) {
            int dpy = sumDays(baseMonths);
            if (dpy <= 0) return new Split(epochYearOffset, 1, 1, 1);
            long yearsElapsed = Math.floorDiv(worldDay, (long) dpy);
            int dayInYear = (int) Math.floorMod(worldDay, (long) dpy);
            int displayYear = (int) (yearsElapsed + epochYearOffset);
            return splitWithinYear(baseMonths, displayYear, dayInYear);
        }
        int cyclePeriod = detectCyclePeriod(leapRules);
        long daysPerCycle = 0L;
        if (cyclePeriod > 0) {
            for (int y = 0; y < cyclePeriod; y++) {
                daysPerCycle += daysInYear(baseMonths, leapRules, epochYearOffset + y);
            }
        }
        long remaining = worldDay;
        int displayYear = epochYearOffset;
        if (daysPerCycle > 0 && cyclePeriod > 0) {
            long fullCycles = Math.floorDiv(remaining, daysPerCycle);
            displayYear = (int) (fullCycles * cyclePeriod + epochYearOffset);
            remaining -= fullCycles * daysPerCycle;
        }
        while (true) {
            int dpy = daysInYear(baseMonths, leapRules, displayYear);
            if (dpy <= 0) {
                return new Split(displayYear, 1, 1, 1);
            }
            if (remaining < 0L) {
                displayYear--;
                int prevDpy = daysInYear(baseMonths, leapRules, displayYear);
                remaining += prevDpy;
                continue;
            }
            if (remaining < dpy) {
                return splitWithinYear(layoutForYear(baseMonths, leapRules, displayYear).months(),
                        displayYear, (int) remaining);
            }
            remaining -= dpy;
            displayYear++;
        }
    }

    /**
     * Convenience overload for callers that don't track an epoch offset
     * (notably the no-leap-rules fast path that already does plain modular
     * math). Equivalent to {@code splitWorldDay(months, rules, day, 0)}.
     */
    public static Split splitWorldDay(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules, long worldDay) {
        return splitWorldDay(baseMonths, leapRules, worldDay, 0);
    }

    /**
     * Sum of all days in the year that fall in months strictly before
     * (1-based) {@code monthIndex}. Used by client-side day-of-week math
     * to find the day-of-year a month starts on.
     */
    public static int daysBeforeMonth(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules, int year, int monthIndex) {
        List<MonthDef> ms = layoutForYear(baseMonths, leapRules, year).months();
        int upTo = Math.min(monthIndex - 1, ms.size());
        int acc = 0;
        for (int i = 0; i < upTo; i++) acc += ms.get(i).days();
        return acc;
    }

    /**
     * Days from (worldDay 0) to the start of {@code displayYear}. Walks
     * from the configured {@code epochYearOffset} (worldDay 0 corresponds
     * to display year {@code epochYearOffset}).
     */
    public static long worldDayAtYearStart(List<MonthDef> baseMonths, @Nullable List<LeapRule> leapRules,
                                           int displayYear, int epochYearOffset) {
        if (leapRules == null || leapRules.isEmpty()) {
            return (long) (displayYear - epochYearOffset) * (long) sumDays(baseMonths);
        }
        long total = 0L;
        if (displayYear >= epochYearOffset) {
            for (int y = epochYearOffset; y < displayYear; y++) total += daysInYear(baseMonths, leapRules, y);
        } else {
            for (int y = displayYear; y < epochYearOffset; y++) total -= daysInYear(baseMonths, leapRules, y);
        }
        return total;
    }

    // ---------- internals ----------

    private static YearLayout compute(List<MonthDef> baseMonths, List<LeapRule> leapRules, int year) {
        List<MonthDef> working = new ArrayList<>(baseMonths);
        // Pending inserts collected first, applied at the end so AdjustDays
        // can reference base indices without being shifted by earlier inserts.
        List<PendingInsert> inserts = new ArrayList<>(0);
        for (LeapRule rule : leapRules) {
            if (!rule.when().test(year)) continue;
            LeapRule.Action a = rule.action();
            if (a instanceof LeapRule.AdjustDays adj) {
                int idx = adj.monthIndex() - 1;
                if (idx < 0 || idx >= working.size()) continue;
                MonthDef cur = working.get(idx);
                int newDays = cur.days() + adj.delta();
                if (newDays <= 0) continue; // refuse to zero/negative a month
                working.set(idx, new MonthDef(cur.commonName(), newDays));
            } else if (a instanceof LeapRule.RenameMonth ren) {
                int idx = ren.monthIndex() - 1;
                if (idx < 0 || idx >= working.size()) continue;
                MonthDef cur = working.get(idx);
                working.set(idx, new MonthDef(ren.newName(), cur.days()));
            } else if (a instanceof LeapRule.InsertMonth ins) {
                inserts.add(new PendingInsert(ins.afterMonthIndex(), ins.month()));
            }
        }
        if (!inserts.isEmpty()) {
            // Apply in descending base-index order so each insert's position
            // is preserved relative to the base list. Appends (null index)
            // come last in stable order.
            inserts.sort((x, y) -> {
                int xi = x.afterMonthIndex == null ? Integer.MIN_VALUE : x.afterMonthIndex;
                int yi = y.afterMonthIndex == null ? Integer.MIN_VALUE : y.afterMonthIndex;
                return Integer.compare(yi, xi);
            });
            for (PendingInsert ins : inserts) {
                if (ins.afterMonthIndex == null) {
                    working.add(ins.month);
                } else {
                    int pos = Math.min(working.size(), Math.max(0, ins.afterMonthIndex));
                    working.add(pos, ins.month);
                }
            }
        }
        return new YearLayout(working, sumDays(working));
    }

    private static Split splitWithinYear(List<MonthDef> months, int year, int dayInYear) {
        int acc = 0;
        for (int i = 0; i < months.size(); i++) {
            int d = months.get(i).days();
            if (dayInYear < acc + d) {
                return new Split(year, i + 1, dayInYear - acc + 1, dayInYear + 1);
            }
            acc += d;
        }
        // Out-of-range — shouldn't happen with correct year math. Pin to last day.
        int lastIdx = Math.max(1, months.size());
        int lastDays = months.isEmpty() ? 1 : months.get(months.size() - 1).days();
        return new Split(year, lastIdx, lastDays, acc);
    }

    private static int sumDays(List<MonthDef> months) {
        int total = 0;
        for (MonthDef m : months) total += m.days();
        return total;
    }

    /**
     * LCM of every {@code mod} appearing in any predicate. Caps the search
     * at a reasonable ceiling so a pathological rule can't blow up cycle
     * sizing. Returns 0 when no modular predicate is found (then the caller
     * skips the cycle optimization).
     */
    private static int detectCyclePeriod(List<LeapRule> rules) {
        long lcm = 1L;
        boolean any = false;
        for (LeapRule rule : rules) {
            long sub = predicateMod(rule.when());
            if (sub > 0L) {
                lcm = lcm(lcm, sub);
                any = true;
                if (lcm > 100_000L) return 0; // bail on absurd cycles
            }
        }
        return any ? (int) lcm : 0;
    }

    private static long predicateMod(LeapRule.Predicate p) {
        if (p instanceof LeapRule.Equals e) return e.mod();
        if (p instanceof LeapRule.In i) return i.mod();
        if (p instanceof LeapRule.AllOf a) {
            long lcm = 1L;
            for (LeapRule.Predicate part : a.parts()) {
                long sub = predicateMod(part);
                if (sub > 0L) lcm = lcm(lcm, sub);
            }
            return lcm;
        }
        if (p instanceof LeapRule.AnyOf a) {
            long lcm = 1L;
            for (LeapRule.Predicate part : a.parts()) {
                long sub = predicateMod(part);
                if (sub > 0L) lcm = lcm(lcm, sub);
            }
            return lcm;
        }
        return 0L;
    }

    private static long lcm(long a, long b) {
        if (a == 0 || b == 0) return 0;
        return Math.abs(a / gcd(a, b) * b);
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a); b = Math.abs(b);
        while (b != 0) { long t = a % b; a = b; b = t; }
        return a;
    }

    /**
     * Drop cached layouts whose backing month/rule lists are no longer
     * referenced. Called on data pack reload so a new profile registry
     * doesn't retain stale cache entries forever.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    private record CacheKey(List<MonthDef> months, List<LeapRule> rules) {
        // System identity equality — caches are keyed by exact list instance,
        // not structural equality. Profile records own their lists; client
        // snapshots own theirs. No need to deep-compare.
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CacheKey ck)) return false;
            return ck.months == months && ck.rules == rules;
        }
        @Override
        public int hashCode() {
            return System.identityHashCode(months) * 31 + System.identityHashCode(rules);
        }
    }

    private record PendingInsert(@Nullable Integer afterMonthIndex, MonthDef month) {}

    /**
     * Decomposition of an absolute worldDay into (year, 1-based monthIndex,
     * 1-based dayOfMonth, 1-based dayOfYear).
     */
    public record Split(int year, int monthIndex, int dayOfMonth, int dayOfYear) {}

    /** Empty year layout used when callers need to defend against missing data. */
    public static final YearLayout EMPTY = new YearLayout(Collections.emptyList(), 0);
}
