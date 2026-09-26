package com.aetherianartificer.townstead.client.tts;

import net.conczin.mca.client.tts.AudioCache;
import net.minecraft.client.Minecraft;

import java.io.OutputStream;
import java.util.function.Consumer;

/**
 * Asks the TTS server a second time for a phrase it had to generate.
 *
 * <p>The request carries {@code load_async}, so a phrase the server has never spoken comes back
 * empty while it is queued, and the line is lost even though the audio exists moments later. One
 * retry catches it while the same line is still on screen. A slower generation is left alone:
 * audio that arrives after the player has read on would talk over the next line.</p>
 */
public final class QueuedPhraseRetry {

    private static final long RETRY_DELAY_MS = 2000L;

    /** Set while the retry is in flight, so a second failure ends there instead of recursing. */
    private static final ThreadLocal<Boolean> RETRYING = ThreadLocal.withInitial(() -> false);

    private QueuedPhraseRetry() {
    }

    public static boolean retryOnce(String identifier, Consumer<OutputStream> retriever) {
        if (RETRYING.get()) return false;
        // Every speech backend fetches off-thread; if that ever changes, dropping the line
        // beats freezing the client for two seconds.
        if (Minecraft.getInstance().isSameThread()) return false;

        try {
            Thread.sleep(RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        RETRYING.set(true);
        try {
            return AudioCache.cachedRetrieve(identifier, retriever);
        } finally {
            RETRYING.set(false);
        }
    }
}
