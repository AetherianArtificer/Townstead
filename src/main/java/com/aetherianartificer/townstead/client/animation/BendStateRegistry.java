package com.aetherianartificer.townstead.client.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-entity store of the last applied bend (axis, angle) per limb, written
 * by {@link McaModelPartApplier} when our animation bridge applies bend. Read
 * by layer-render mixins to re-apply the bend on the LAYER model's wear
 * parts after MCA's {@code copyPropertiesTo} runs — because relying on
 * bendylib's {@code copyTransformExtended} to propagate state across the
 * separate {@code VillagerEntityModelMCA} instances in MCA's layer pipeline
 * has proved unreliable in practice.
 *
 * <p>Single-threaded usage: writes and reads both happen on the render
 * thread within one frame, so a plain {@code HashMap} is enough.</p>
 */
public final class BendStateRegistry {
    public record State(float axis, float angle) {}

    private static final Map<UUID, Map<String, State>> STATES = new HashMap<>();

    private BendStateRegistry() {}

    public static void put(UUID entityId, String partName, float axis, float angle) {
        STATES.computeIfAbsent(entityId, k -> new HashMap<>()).put(partName, new State(axis, angle));
    }

    public static State get(UUID entityId, String partName) {
        Map<String, State> m = STATES.get(entityId);
        return m == null ? null : m.get(partName);
    }

    public static void clearEntity(UUID entityId) {
        STATES.remove(entityId);
    }
}
