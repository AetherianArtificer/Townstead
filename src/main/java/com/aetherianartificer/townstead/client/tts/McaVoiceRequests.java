package com.aetherianartificer.townstead.client.tts;

import net.conczin.mca.client.tts.SpeechManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * MCA builds that track voice requests play a line only if it is still the speaker's latest request,
 * and cut off the line already playing. Older builds play every line as it arrives.
 */
public final class McaVoiceRequests {
    private static final long NONE = -1L;
    private static final Method BEGIN = method("beginVoiceRequest", UUID.class);
    private static final Method PLAY_REQUEST = method("playSound", float.class, Entity.class, ResourceLocation.class, long.class);
    private static final Method PLAY = method("playSound", float.class, Entity.class, ResourceLocation.class);

    private McaVoiceRequests() {}

    /** Claims the speaker's next line; call before the audio is fetched, as MCA does. */
    public static long begin(Entity speaker) {
        if (BEGIN == null) return NONE;
        try {
            return (long) BEGIN.invoke(SpeechManager.INSTANCE, speaker.getUUID());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return NONE;
        }
    }

    public static void play(float pitch, Entity speaker, ResourceLocation sound, long request) {
        try {
            if (PLAY_REQUEST != null && request != NONE) {
                PLAY_REQUEST.invoke(SpeechManager.INSTANCE, pitch, speaker, sound, request);
            } else if (PLAY != null) {
                PLAY.invoke(SpeechManager.INSTANCE, pitch, speaker, sound);
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static Method method(String name, Class<?>... parameters) {
        try {
            Method method = SpeechManager.class.getDeclaredMethod(name, parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
            return null;
        }
    }
}
