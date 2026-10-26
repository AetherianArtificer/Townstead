package com.aetherianartificer.townstead.client.input;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What the mod that OWNS a binding says about it right now.
 *
 * <p>The layer a resource pack cannot reach. "Quick Cast Slot 1" is not a spell, it is an INDEX
 * into whatever spellbook the player is carrying, so a pack declaring an icon for it is labelling a
 * slot whose contents change the moment you swap books. Only the owning mod knows what is in there,
 * and only right now.</p>
 *
 * <p>A seam rather than a special case: any mod bridge can answer for its own bindings, and one
 * that is not installed simply never registers. Sources are asked in registration order and the
 * first to claim a binding keeps it, the same rule {@code Assignables} uses.</p>
 */
public final class LiveKeybinds {

    private LiveKeybinds() {}

    /** Answers for the bindings one mod owns, and returns null for everything else. */
    public interface Source {
        /** Details as they stand, or null when this source does not own the binding. */
        KeybindDetails.Detail resolve(String keybind);
    }

    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();

    public static void register(Source source) {
        if (source != null && !SOURCES.contains(source)) SOURCES.add(source);
    }

    /** The first source to claim this binding, or {@link KeybindDetails.Detail#NONE}. */
    public static KeybindDetails.Detail resolve(String keybind) {
        if (keybind == null || keybind.isEmpty()) return KeybindDetails.Detail.NONE;
        for (Source source : SOURCES) {
            try {
                KeybindDetails.Detail detail = source.resolve(keybind);
                if (detail != null) return detail;
            } catch (Exception ignored) {
                // A bridge that throws has declined. Its mod may have changed under us, and that
                // must cost its own bindings rather than the whole catalogue.
            }
        }
        return KeybindDetails.Detail.NONE;
    }
}
