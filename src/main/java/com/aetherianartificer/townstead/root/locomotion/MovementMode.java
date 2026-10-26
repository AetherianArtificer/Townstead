package com.aetherianartificer.townstead.root.locomotion;

/**
 * How a mob is moving right now. {@link #GROUND} is MCA's own navigation; every other
 * mode hands the body to a Townstead {@link Pilot}.
 *
 * <ul>
 * <li>{@link #AIR}: a flight; navigation, move control and jumping all sit out.</li>
 * <li>{@link #DRIFT}: a ghost's permanent float. MCA's navigation keeps planning and
 * opening doors; the pilot moves the body toward where it points. When walking can't
 * reach, the pilot takes over navigation too.</li>
 * <li>{@link #WATER}: a swim through water; navigation, move control and jumping sit out.</li>
 * </ul>
 */
public enum MovementMode {
    GROUND,
    AIR,
    DRIFT,
    WATER
}
