package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-side helper for AI hooks that want to broadcast an emote on a tracked
 * entity. Builds an {@link EmoteTriggerS2CPayload} and routes it to the entity's
 * trackers (and to the entity itself if it's a player).
 *
 * <p>Specific AI hookups (greet, panic, festival cheer) are added by callers; this
 * class is just plumbing.</p>
 */
public final class AiEmoteScheduler {
    private AiEmoteScheduler() {}

    public static void playEmote(LivingEntity entity, ResourceLocation emoteId) {
        playEmote(entity, emoteId, (byte) -1, 1.0F);
    }

    /**
     * Broadcast a stop. Implemented as an emote trigger with the sentinel
     * empty-string emote id; the client handler interprets that as a stop request.
     */
    public static void stopEmote(LivingEntity entity) {
        if (entity == null) return;
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof ServerPlayer)) return;

        EmoteTriggerS2CPayload payload = new EmoteTriggerS2CPayload(
                entity.getId(), "", (byte) -1, 1.0F);

        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer sp) {
            com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, payload);
        }
        *///?}
    }

    public static void playEmote(
            LivingEntity entity,
            ResourceLocation emoteId,
            byte loopOverride,
            float speed
    ) {
        if (entity == null || emoteId == null) return;
        if (entity.level().isClientSide()) return;
        if (!(entity instanceof VillagerEntityMCA) && !(entity instanceof ServerPlayer)) return;

        EmoteTriggerS2CPayload payload = new EmoteTriggerS2CPayload(
                entity.getId(), emoteId.toString(), loopOverride, speed);

        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToTrackingEntity(entity, payload);
        if (entity instanceof ServerPlayer sp) {
            com.aetherianartificer.townstead.TownsteadNetwork.sendToPlayer(sp, payload);
        }
        *///?}

        Townstead.LOGGER.debug("[AnimationBridge] AI emote scheduled: entity={} emote={}",
                entity.getId(), emoteId);
    }
}
