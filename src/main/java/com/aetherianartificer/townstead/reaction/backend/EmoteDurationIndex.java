package com.aetherianartificer.townstead.reaction.backend;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Server-side duration table for the eleven Emotecraft built-in emotes.
 * The server can't see {@code assets/} resources on a dedicated host, so
 * the dispatcher can't compute lock durations from the underlying JSON
 * at runtime. We instead hard-code the figures pulled directly from
 * Emotecraft's source ({@code emotesAssets/.../emotes/<name>.json}):
 *
 * <ul>
 *   <li>One-shot emotes: lock for {@code stopTick}.
 *   <li>Loops: first pass to {@code endTick}, then {@code shots-1} more
 *       cycles of {@code endTick - returnTick}.
 * </ul>
 *
 * Unknown emotes (user-shipped data-pack emotes, locally-loaded ones)
 * return {@link Optional#empty()} and the dispatcher falls back to the
 * reaction's {@code lock_ticks}.
 */
public final class EmoteDurationIndex {
    private record Spec(boolean isLoop, int firstPassEnd, int loopLength) {
        int ticksFor(int shots) {
            if (!isLoop) return firstPassEnd;
            return firstPassEnd + Math.max(0, shots - 1) * loopLength;
        }

        static Spec once(int stopTick) {
            return new Spec(false, stopTick, 0);
        }

        static Spec loop(int endTick, int returnTick) {
            int len = Math.max(1, endTick - returnTick);
            return new Spec(true, endTick, len);
        }
    }

    private static final Map<String, Spec> BUILTINS = Map.ofEntries(
            Map.entry("backflip", Spec.once(81)),
            Map.entry("clap", Spec.once(46)),
            Map.entry("club_penguin_dance", Spec.loop(152, 0)),
            Map.entry("crying", Spec.once(104)),
            Map.entry("here", Spec.once(78)),
            Map.entry("kazotsky_kick", Spec.loop(61, 11)),
            Map.entry("palm", Spec.once(44)),
            Map.entry("point", Spec.once(83)),
            Map.entry("roblox_potion_dance", Spec.loop(61, 0)),
            Map.entry("twerk", Spec.loop(14, 6)),
            Map.entry("waving", Spec.once(50))
    );

    private EmoteDurationIndex() {}

    public static Optional<Integer> ticksFor(String emoteName, int shots) {
        if (emoteName == null) return Optional.empty();
        Spec spec = BUILTINS.get(emoteName.toLowerCase(Locale.ROOT));
        if (spec == null) return Optional.empty();
        return Optional.of(Math.max(1, spec.ticksFor(Math.max(1, shots))));
    }
}
