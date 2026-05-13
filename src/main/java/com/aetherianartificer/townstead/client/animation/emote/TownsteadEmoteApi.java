package com.aetherianartificer.townstead.client.animation.emote;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Public client-side entry point for triggering emotes. Both AI-driven (S2C packet)
 * and command-driven paths funnel through here.
 *
 * <p>If Emotecraft isn't installed, or the requested emote isn't in the registry,
 * the trigger silently no-ops and returns {@code false}. Callers shouldn't gate on
 * the return value for user feedback — the command layer reports "not installed"
 * separately.</p>
 */
public final class TownsteadEmoteApi {
    private TownsteadEmoteApi() {}

    public static boolean trigger(LivingEntity entity, ResourceLocation emoteId) {
        return trigger(entity, emoteId, null, 1.0F);
    }

    /**
     * @param loopOverride if non-null, overrides the parsed emote's own {@code
     *                     loopType} for this playback only.
     * @param speed        speed multiplier; 1.0 plays at authored speed.
     * @return true if a playback was scheduled; false if the emote isn't loaded.
     */
    public static boolean trigger(
            LivingEntity entity,
            ResourceLocation emoteId,
            ParsedEmote.LoopType loopOverride,
            float speed
    ) {
        if (entity == null || emoteId == null) return false;
        ParsedEmote emote = EmoteRegistry.get(emoteId).orElse(null);
        if (emote == null) {
            // Reaction-system fallback: data packs reference emotes by lowercased
            // name (e.g. "emotecraft:Wave" -> "wave") with a synthetic namespace.
            // Find any loaded emote whose path matches, regardless of namespace.
            String path = emoteId.getPath();
            for (ResourceLocation candidate : EmoteRegistry.allIds()) {
                if (candidate.getPath().equalsIgnoreCase(path)) {
                    emote = EmoteRegistry.get(candidate).orElse(null);
                    if (emote != null) {
                        emoteId = candidate;
                        break;
                    }
                }
            }
            if (emote == null) return false;
        }

        ParsedEmote.LoopType loopType = loopOverride != null ? loopOverride : emote.loopType();
        float clampedSpeed = (speed > 0F && Float.isFinite(speed)) ? speed : 1.0F;
        long now = entity.level().getGameTime();
        EmotePlaybackRegistry.put(
                entity.getUUID(),
                EmotePlayback.fresh(emoteId, now, loopType, clampedSpeed));
        return true;
    }

    /**
     * Begin a smooth fade-out instead of an instant stop. The adapter captures the
     * pose on the first frame after this call and ramps blend down to zero over
     * {@code durationTicks} before evicting the playback. A duration of {@code
     * 0} (or less) makes the adapter use its default fade-out window.
     */
    public static void stop(LivingEntity entity) {
        stop(entity, 0F);
    }

    public static void stop(LivingEntity entity, float durationTicks) {
        if (entity == null) return;
        EmotePlayback existing = EmotePlaybackRegistry.get(entity.getUUID());
        if (existing == null) return;
        if (existing.isFadingOut()) return;
        long now = entity.level().getGameTime();
        EmotePlaybackRegistry.put(
                entity.getUUID(),
                existing.startFadeOut(now, -1F, durationTicks));
    }
}
