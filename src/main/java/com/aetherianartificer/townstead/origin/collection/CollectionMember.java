package com.aetherianartificer.townstead.origin.collection;

/**
 * A collection member's stored state: an integer {@code count} (the tally; 1 for a plain-set member)
 * and an {@code expiry} game-time ({@link Long#MAX_VALUE} = permanent). A plain set is just the
 * count-is-one case, so membership and tally share one store.
 */
final class CollectionMember {

    int count;
    long expiry;

    CollectionMember(int count, long expiry) {
        this.count = count;
        this.expiry = expiry;
    }
}
