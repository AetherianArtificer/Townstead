package com.aetherianartificer.townstead.chronicle.scope;

import com.aetherianartificer.townstead.chronicle.store.ChronicleSavedData;

/**
 * The scopes that exist today and the retention numbers they were tuned to.
 *
 * <p>Significance is anchored: 1 is private, 3 is the village noticing, 4 is a
 * life event, 5 is remembered for years, 10 is a generation remembering.
 *
 * <p>Thresholds sit <em>between</em> anchors, never on one. A threshold equal to
 * an anchor turns magnitude noise into a coin toss, which is how half the births
 * in a village were landing outside its own digest. At 3.5 a village keeps every
 * life event and only the feasts and harvests that were genuinely good ones.</p>
 */
public final class ChronicleScopes {

    /**
     * Above the protagonist multiplier of 3, so being at the centre of something
     * is not on its own enough to keep it: a middling friendship fades, a real
     * one stays, and a feast you cooked (3 x 3) always does.
     */
    public static final ScopeProfile PERSON = new ScopeProfile(
            "person", 3.5f,
            ChronicleSavedData.MAX_MEMORIES_PER_VILLAGER,
            ChronicleSavedData.MEMORY_DAILY_DECAY);

    /** The village digest: bounded and ordered, but not decaying. */
    public static final ScopeProfile VILLAGE = new ScopeProfile("village", 3.5f, 256, 1.0f);

    private ChronicleScopes() {}
}
