package com.aetherianartificer.townstead.profession.def;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Where along a career's XP Insight is paid. Each rank span pays at evenly spaced checkpoints and
 * again on the rank-up that closes it; the first rank pays nothing, so a single action in a career
 * earns no Insight. Past the top rank, one Insight lands per span the size of the final rank span.
 * Everything is derived from XP, so a changed schedule re-prices a save without a migration.
 */
public final class InsightSchedule {

    private InsightSchedule() {}

    /**
     * Insight paid out at this XP total. {@code rankPayout} gives the points for reaching a
     * 1-based level (called for levels 2 and up).
     */
    public static int earnedAt(ProgressionTrack track, IntUnaryOperator rankPayout, int xp) {
        List<Integer> t = track.tierThresholds();
        int checkpoints = Math.max(0, track.checkpointsPerRank());
        int total = 0;
        for (int i = 0; i + 1 < t.size(); i++) {
            int lo = t.get(i);
            int hi = t.get(i + 1);
            for (int k = 1; k <= checkpoints; k++) {
                if (xp >= checkpointAt(lo, hi, k, checkpoints)) total++;
            }
            if (xp >= hi) total += Math.max(0, rankPayout.applyAsInt(i + 2));
        }
        int tail = tailSpan(t);
        if (tail > 0) {
            int top = t.get(t.size() - 1);
            if (xp > top) total += (Math.min(xp, track.maxXp()) - top) / tail;
        }
        return total;
    }

    /** XP totals, ascending, at which any Insight lands, no higher than {@code limit}. */
    public static List<Integer> marksThrough(ProgressionTrack track, int limit) {
        List<Integer> t = track.tierThresholds();
        int checkpoints = Math.max(0, track.checkpointsPerRank());
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i + 1 < t.size(); i++) {
            int lo = t.get(i);
            int hi = t.get(i + 1);
            for (int k = 1; k <= checkpoints; k++) {
                int at = checkpointAt(lo, hi, k, checkpoints);
                if (at <= limit) out.add(at);
            }
            if (hi <= limit) out.add(hi);
        }
        int tail = tailSpan(t);
        if (tail > 0) {
            int cap = Math.min(limit, track.maxXp());
            for (long at = (long) t.get(t.size() - 1) + tail; at <= cap; at += tail) {
                out.add((int) at);
            }
        }
        return out;
    }

    /** The XP total where the next Insight lands after {@code xp}, or -1 when none remains. */
    public static int nextAfter(ProgressionTrack track, int xp) {
        List<Integer> t = track.tierThresholds();
        int checkpoints = Math.max(0, track.checkpointsPerRank());
        for (int i = 0; i + 1 < t.size(); i++) {
            int lo = t.get(i);
            int hi = t.get(i + 1);
            for (int k = 1; k <= checkpoints; k++) {
                int at = checkpointAt(lo, hi, k, checkpoints);
                if (at > xp) return at;
            }
            if (hi > xp) return hi;
        }
        int tail = tailSpan(t);
        if (tail <= 0) return -1;
        int top = t.get(t.size() - 1);
        long next = top + ((long) Math.max(0, xp - top) / tail + 1) * tail;
        return next > track.maxXp() ? -1 : (int) next;
    }

    /** The XP total where the Insight before {@code xp}'s next one landed (0 at the start). */
    public static int previousAtOrBefore(ProgressionTrack track, int xp) {
        int previous = 0;
        for (int at : marksThrough(track, xp)) previous = at;
        return previous;
    }

    private static int checkpointAt(int lo, int hi, int k, int checkpoints) {
        int span = hi - lo;
        int offset = (int) ((long) span * k / (checkpoints + 1));
        return Math.min(hi, lo + Math.max(1, offset));
    }

    private static int tailSpan(List<Integer> t) {
        if (t.size() < 2) return 0;
        return Math.max(0, t.get(t.size() - 1) - t.get(t.size() - 2));
    }
}
