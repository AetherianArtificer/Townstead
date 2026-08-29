package com.aetherianartificer.townstead.data;

/**
 * A counter bumped when datapacks finish reloading, for caches whose contents are derived from
 * pack data and therefore cannot change in between.
 *
 * <p>Deliberately holds nothing but an int. Caches that key off it are consulted from hot paths
 * and from unit tests, so reading the token must never pull a registry-touching class into
 * initialization.</p>
 */
public final class ReloadGeneration {

    private static volatile int generation = 0;

    private ReloadGeneration() {}

    public static int current() {
        return generation;
    }

    public static void bump() {
        generation++;
    }
}
