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
        TownsteadEmoteApi.trigger(living, id, override, payload.speed());
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
