package com.aetherianartificer.townstead.origin;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Which body animations a species' rig performs, and where each one comes from. A generalizable
 * seam: a humanoid-derived rig (the skeletownie) inherits the standard humanoid poses for free,
 * while a future non-humanoid rig (spider, horse) opts a state out with {@code none}, or, later,
 * points it at a custom pose. Authored on the species as
 * {@code "animations": { "providers": ["minecraft:skeleton","minecraft:player","humanoid"],
 * "crouch": "humanoid", "sleep": "humanoid", "fly": "humanoid" }}.
 *
 * <p>{@code sources} are the per-state pose origins (unlisted -> {@link Source#HUMANOID}, opt-out).
 * {@code providers} is the animation-bridge fall-through chain: a list of entity identities whose
 * Fresh-Animations/EMF CEM should drive the rig's idle/walk, tried in order, with {@code "humanoid"}
 * meaning "our own setupAnim", the always-available floor. Empty -> just the base.</p>
 */
public record Animations(Map<State, Source> sources, List<String> providers) {

    /** No overrides: every state resolves to {@link Source#HUMANOID}; no FA providers. */
    public static final Animations DEFAULT = new Animations(Map.of(), List.of());

    /** A pose-state the rig can be asked to perform. New states slot in without a format change. */
    public enum State {
        CROUCH("crouch"), SLEEP("sleep"), FLY("fly");

        private final String key;

        State(String key) { this.key = key; }

        public String key() { return key; }

        public static State byKey(String raw) {
            if (raw == null) return null;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (State state : values()) if (state.key.equals(needle)) return state;
            return null;
        }
    }

    /** Where a state's pose comes from. {@code custom} (rig-authored) is a reserved future kind. */
    public enum Source {
        HUMANOID("humanoid"), NONE("none");

        private final String key;

        Source(String key) { this.key = key; }

        public String key() { return key; }

        public static Source byKey(String raw, Source fallback) {
            if (raw == null) return fallback;
            String needle = raw.toLowerCase(Locale.ROOT);
            for (Source source : values()) if (source.key.equals(needle)) return source;
            return fallback;
        }
    }

    /** The source for a state, defaulting to {@link Source#HUMANOID} when unlisted (opt-out). */
    public Source source(State state) {
        return sources.getOrDefault(state, Source.HUMANOID);
    }

    public boolean isHumanoid(State state) {
        return source(state) == Source.HUMANOID;
    }
}
