package com.aetherianartificer.townstead.devlink;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * The seam between Bench Link (server) and the render-time anchor capture (client). Anchors are
 * what the renderer actually computed for an entity's bones and attachments, so they exist only
 * where a game client runs in the same process: single player, or a LAN host. The client installs
 * a {@link Source} at setup; a dedicated server has none and the endpoint answers 501.
 */
public final class BenchAnchors {

    /** Captures one entity's anchors on its next rendered frame. */
    public interface Source {
        /** Blocks up to {@code timeoutMs} for a fresh capture, or returns null if it was not drawn. */
        @Nullable JsonObject capture(int entityId, long timeoutMs) throws InterruptedException;
    }

    private static volatile @Nullable Source source;

    private BenchAnchors() {}

    public static void install(Source captureSource) {
        source = captureSource;
    }

    static boolean available() {
        return source != null;
    }

    static @Nullable JsonObject capture(int entityId, long timeoutMs) throws InterruptedException {
        Source current = source;
        return current == null ? null : current.capture(entityId, timeoutMs);
    }
}
