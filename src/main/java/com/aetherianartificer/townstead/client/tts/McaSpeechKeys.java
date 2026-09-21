package com.aetherianartificer.townstead.client.tts;

import net.conczin.mca.entity.CommonSpeechManager;
import net.minecraft.network.chat.Component;

/**
 * Keeps MCA's record of "which translation key produced this line" honest.
 *
 * <p>MCA parks the key of the last pooled lookup in {@code lastResolvedKey} and the next component
 * that resolves claims it. A key MCA does not own, such as a Townstead conversation line, misses the
 * pool and leaves the field untouched, so that line claims the key of whatever MCA resolved before
 * it and is then spoken with the wrong audio. Clearing the field first means a miss records nothing
 * and the line is recognised as one Townstead has to voice itself.</p>
 */
public final class McaSpeechKeys {

    private McaSpeechKeys() {
    }

    /** Drops any key left over from an earlier line. Call before resolving a line of unknown origin. */
    public static void clearPending() {
        CommonSpeechManager.INSTANCE.lastResolvedKey = null;
    }

    /**
     * Whether MCA can name the translation key behind this line, and so speak it with the player's
     * chosen TTS. Only meaningful once the line has been resolved.
     */
    public static boolean isKeyed(Component line) {
        return CommonSpeechManager.INSTANCE.translations.containsKey(line.getContents());
    }
}
