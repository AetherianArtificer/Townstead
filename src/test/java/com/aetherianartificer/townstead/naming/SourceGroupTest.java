package com.aetherianartificer.townstead.naming;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Groups are a second chance, not a blend. The case that matters: a culture blending its own names
 * with another mod's must fall back to its own alone when that mod is absent, rather than quietly
 * becoming a differently weighted blend of whatever survived.
 */
class SourceGroupTest {

    private static NamingTradition.SourceGroup group(NamingTradition.SourceGroup.Requirement require,
                                                     String... lists) {
        return new NamingTradition.SourceGroup(
                List.of(java.util.Arrays.stream(lists)
                        .map(l -> new NamingTradition.GivenSource(l, 1.0F))
                        .toArray(NamingTradition.GivenSource[]::new)),
                require);
    }

    private static Predicate<String> loaded(String... present) {
        Set<String> set = Set.of(present);
        return set::contains;
    }

    @Test void requireAllPassesOverAPartialGroup() {
        var strict = group(NamingTradition.SourceGroup.Requirement.ALL, "own", "mca:elven");
        assertTrue(strict.satisfied(loaded("own", "mca:elven")));
        assertFalse(strict.satisfied(loaded("own")), "a partial blend is not what was asked for");
        assertFalse(strict.satisfied(loaded()));
    }

    @Test void requireAnyKeepsWhateverResolved() {
        var forgiving = group(NamingTradition.SourceGroup.Requirement.ANY, "own", "mca:elven");
        assertTrue(forgiving.satisfied(loaded("own")));
        assertTrue(forgiving.satisfied(loaded("mca:elven")));
        assertFalse(forgiving.satisfied(loaded()), "nothing resolving is not a usable group");
    }

    @Test void emptyGroupIsNeverUsable() {
        assertFalse(new NamingTradition.SourceGroup(List.of(),
                NamingTradition.SourceGroup.Requirement.ALL).satisfied(loaded("own")));
        assertFalse(new NamingTradition.SourceGroup(List.of(),
                NamingTradition.SourceGroup.Requirement.ANY).satisfied(loaded("own")));
    }

    @Test void aBareReferenceIsAGroupOfOne() {
        var single = NamingTradition.SourceGroup.of("own");
        assertEquals(1, single.from().size());
        assertEquals(NamingTradition.SourceGroup.Requirement.ANY, single.require());
        assertTrue(single.satisfied(loaded("own")));
        assertFalse(single.satisfied(loaded("other")));
    }

    @Test void zeroRateMembersDoNotSatisfyAGroup() {
        var muted = new NamingTradition.SourceGroup(
                List.of(new NamingTradition.GivenSource("own", 0.0F)),
                NamingTradition.SourceGroup.Requirement.ANY);
        assertFalse(muted.satisfied(loaded("own")), "a source weighted to nothing supplies nothing");
    }
}
