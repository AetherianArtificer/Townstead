package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.performance.NativePerformanceS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/** Applies server clip events to the client-side playback registry. */
public final class NativePerformanceClientHandler {
    private NativePerformanceClientHandler() {}
    public static void handle(NativePerformanceS2CPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) {
            Townstead.LOGGER.warn("[NativePerformance] discarded {} because no client level is active", payload.clipId());
            return;
        }
        if (payload.clipId().isBlank() || payload.durationTicks() <= 0) {
            NativePlaybackRegistry.remove(payload.entityId(), payload.channel());
            return;
        }
        ResourceLocation clip;
        try {
            //? if neoforge {
            clip = ResourceLocation.parse(payload.clipId());
            //?} else {
            /*clip = new ResourceLocation(payload.clipId());
            *///?}
        } catch (Exception exception) {
            Townstead.LOGGER.warn("[NativePerformance] rejected invalid clip id '{}'", payload.clipId());
            return;
        }
        if (!NativeClipRegistry.contains(clip)) {
            Townstead.LOGGER.warn("[NativePerformance] client has no loaded clip {}", clip);
            return;
        }
        // Only real consumption may start drinking gestures; ambient beats have no serving.
        if ((clip.getPath().equals("sip") || clip.getPath().equals("toast"))
                && !payload.channel().equals("consumption")) return;
        // Key by the synchronized entity id carried by the payload, not UUID resolved from the
        // client world. A performance can arrive in the same network turn as entity tracking;
        // accepting it here avoids silently dropping the first gesture before the entity exists.
        long now = client.level.getGameTime();
        int seed = java.util.Objects.hash(payload.entityId(), payload.channel(), clip, now);
        boolean vary = SocialPlaybackTiming.varies(payload.channel());
        int delay = vary ? SocialPlaybackTiming.delay(seed, payload.durationTicks()) : 0;
        float speed = vary ? SocialPlaybackTiming.speed(seed) : 1F;
        NativePlaybackRegistry.put(payload.entityId(), payload.channel(),
                new NativePlaybackRegistry.Playback(clip, now + delay, now + payload.durationTicks(), payload.priority(), speed));
        if ("townstead_social_debug".equals(payload.channel())) {
            Townstead.LOGGER.info("[NativePerformance] client accepted {} for entity {} ({} ticks)",
                    clip, payload.entityId(), payload.durationTicks());
        }
    }
}
