package com.aetherianartificer.townstead.hangout;

import java.util.function.Function;
import java.util.function.Predicate;

/** Chooses a free approach, preferring reachable candidates over useful partial paths. */
final class HangoutApproaches {
    enum Reachability { REACHABLE, PARTIAL, UNREACHABLE }

    private HangoutApproaches() {}

    static <T> T select(Iterable<T> candidates, Predicate<T> available,
                        Function<T, Reachability> reachability) {
        T partial = null;
        for (T candidate : candidates) {
            // A reserved approach must not hide another way to reach the same seat.
            if (!available.test(candidate)) continue;
            Reachability result = reachability.apply(candidate);
            if (result == Reachability.REACHABLE) return candidate;
            if (result == Reachability.PARTIAL && partial == null) partial = candidate;
        }
        return partial;
    }
}
