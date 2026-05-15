package com.aetherianartificer.townstead.client.animation.emote;

import com.aetherianartificer.townstead.emote.EmoteTriggerS2CPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client-side dispatch for {@link EmoteTriggerS2CPayload}. Looks up the target
 * entity by network id and either triggers or stops an emote (empty {@code emoteId}
 * is the stop sentinel).
 */
public final class EmoteClientHandler {
    private EmoteClientHandler() {}

    public static void handle(EmoteTriggerS2CPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return;
        Entity entity = client.level.getEntity(payload.entityId());
        if (!(entity instanceof LivingEntity living)) return;

        if (payload.emoteId().isEmpty()) {
            TownsteadEmoteApi.stop(living);
            return;
        }

        ResourceLocation id = parseId(payload.emoteId());
        if (id == null) return;

        ParsedEmote.LoopType override = decodeLoopOverride(payload.loopOverride());
        java.util.Set<String> skippedBones = parseSkippedBones(payload.skippedBones());
        TownsteadEmoteApi.trigger(living, id, override, payload.speed(), payload.mobile(), skippedBones);
    }

    private static java.util.Set<String> parseSkippedBones(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Set.of();
        // Expand group names ("legs", "arms", etc.) to the concrete bone
        // keys the EmotecraftAnimationSourceAdapter knows about.
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String token : raw.split(",")) {
            String t = token.trim().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty()) continue;
            switch (t) {
                case "legs" -> { out.add("left_leg"); out.add("right_leg"); }
                case "arms" -> { out.add("left_arm"); out.add("right_arm"); }
                case "torso" -> { out.add("body"); out.add("torso"); }
                default -> out.add(t);
            }
        }
        return java.util.Set.copyOf(out);
    }

    private static ResourceLocation parseId(String raw) {
        try {
            //? if neoforge {
            return ResourceLocation.parse(raw);
            //?} else {
            /*return new ResourceLocation(raw);
            *///?}
        } catch (Exception e) {
            return null;
        }
    }

    private static ParsedEmote.LoopType decodeLoopOverride(byte b) {
        if (b < 0) return null;
        ParsedEmote.LoopType[] values = ParsedEmote.LoopType.values();
        if (b >= values.length) return null;
        return values[b];
    }
}
