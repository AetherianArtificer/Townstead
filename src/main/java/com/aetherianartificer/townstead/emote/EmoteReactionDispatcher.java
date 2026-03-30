package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.TownsteadConfig;
import com.aetherianartificer.townstead.animation.VillagerResponseAnimation;
import com.aetherianartificer.townstead.animation.VillagerResponseAnimationPayload;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.schedule.Activity;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?} else if forge {
/*import com.aetherianartificer.townstead.TownsteadNetwork;*/
//?}

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EmoteReactionDispatcher {
    private static final VillagerEmotePlaybackBackend PLAYBACK_BACKEND = new EmotecraftReflectionBackend();
    private static final Map<UUID, ReactionState> STATE = new ConcurrentHashMap<>();
    private static final Map<String, Long> DEBUG_LOG_COOLDOWN = new ConcurrentHashMap<>();

    private EmoteReactionDispatcher() {}

    public static void dispatchPlayerEmote(String providerId, ServerPlayer player, ActivePlayerEmote activeEmote) {
        if (player == null || activeEmote == null) return;
        if (!TownsteadConfig.isVillagerEmoteReactionsEnabled()) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        debugLog(level.getGameTime(),
                "event:" + providerId + ":" + player.getUUID() + ":" + activeEmote.uuid(),
                "Observed " + providerId + " emote for " + player.getScoreboardName() + ": aliases=" + activeEmote.aliases());

        EmoteReactionDefinition reaction = EmoteReactionRegistry.findMatch(activeEmote.aliases());
        if (reaction == null) {
            debugLog(level.getGameTime(),
                    "unmatched:" + providerId + ":" + player.getUUID() + ":" + activeEmote.uuid(),
                    "No Townstead emote reaction matched " + providerId + " aliases=" + activeEmote.aliases());
            return;
        }

        int searchRadius = Math.max(1, reaction.radius());
        for (VillagerEntityMCA villager : level.getEntitiesOfClass(
                VillagerEntityMCA.class,
                player.getBoundingBox().inflate(searchRadius),
                EmoteReactionDispatcher::canConsiderVillager
        )) {
            if (villager.distanceToSqr(player) > (double) reaction.radius() * reaction.radius()) continue;

            ReactionState state = STATE.computeIfAbsent(villager.getUUID(), id -> new ReactionState());
            if (!state.canReact(player.getUUID(), activeEmote.uuid(), level.getGameTime(), reaction.cooldownTicks())) {
                debugLog(level.getGameTime(),
                        "cooldown:" + villager.getUUID() + ":" + player.getUUID() + ":" + activeEmote.uuid(),
                        "Villager " + villager.getName().getString() + " suppressed " + providerId + " reaction '" + reaction.id() + "' due to cooldown");
                continue;
            }

            EmoteReactionCandidate candidate = EmoteReactionSelector.pick(
                    reaction.candidates(),
                    personalityOf(villager),
                    level.random
            );
            if (candidate == null) continue;

            debugLog(level.getGameTime(),
                    "matched:" + providerId + ":" + villager.getUUID() + ":" + player.getUUID() + ":" + activeEmote.uuid(),
                    "Villager " + villager.getName().getString() + " matched reaction '" + reaction.id() + "' from " + providerId + " aliases=" + activeEmote.aliases());

            VillagerResponseAnimation responseAnimation = VillagerResponseAnimation.fromId(candidate.villagerEmote());
            if (responseAnimation != null) {
                VillagerResponseAnimationPayload payload = new VillagerResponseAnimationPayload(
                        villager.getId(),
                        responseAnimation.id(),
                        level.getGameTime(),
                        responseAnimation.durationTicks()
                );
                //? if neoforge {
                PacketDistributor.sendToPlayersTrackingEntity(villager, payload);
                //?} else if forge {
                /*TownsteadNetwork.sendToTrackingEntity(villager, payload);*/
                //?}
            }

            if (!candidate.villagerEmote().isBlank()) {
                PLAYBACK_BACKEND.tryPlayVillagerEmote(villager, candidate.villagerEmote());
            }

            villager.getLookControl().setLookAt(player, 30.0F, 30.0F);
            villager.playSpeechEffect();

            boolean forceVisibleFallback = !PLAYBACK_BACKEND.canPlayVillagerEmote();
            if (TownsteadConfig.isVillagerEmoteReactionChatEnabled()
                    && !candidate.chatKeyPrefix().isBlank()
                    && candidate.chatVariants() > 0
                    && (forceVisibleFallback || level.random.nextDouble() <= candidate.chatChance())) {
                villager.sendChatToAllAround(candidate.chatKeyPrefix() + "/" + (1 + level.random.nextInt(candidate.chatVariants())));
            }

            state.recordReaction(player.getUUID(), activeEmote.uuid(), level.getGameTime());
        }
    }

    private static boolean canConsiderVillager(VillagerEntityMCA villager) {
        if (villager == null || !villager.isAlive() || villager.isRemoved()) {
            if (villager != null) STATE.remove(villager.getUUID());
            return false;
        }
        if (villager.isSleeping() || villager.getVillagerBrain().isPanicking()) return false;
        return currentActivity(villager) != Activity.REST;
    }

    private static Activity currentActivity(VillagerEntityMCA villager) {
        long dayTime = villager.level().getDayTime() % 24000L;
        return villager.getBrain().getSchedule().getActivityAt((int) dayTime);
    }

    private static Personality personalityOf(VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getPersonality();
    }

    private static void debugLog(long gameTime, String key, String message) {
        if (!TownsteadConfig.DEBUG_VILLAGER_AI.get()) return;
        Long last = DEBUG_LOG_COOLDOWN.get(key);
        if (last != null && (gameTime - last) < 40L) return;
        DEBUG_LOG_COOLDOWN.put(key, gameTime);
        Townstead.LOGGER.info("[EmoteDebug] {}", message);
    }

    private static final class ReactionState {
        private final Map<UUID, PlayerReactionState> playerState = new HashMap<>();

        boolean canReact(UUID playerId, UUID emoteId, long gameTime, int cooldownTicks) {
            PlayerReactionState state = playerState.get(playerId);
            if (state == null) return true;
            if (state.lastEmoteId.equals(emoteId) && (gameTime - state.lastReactionTick) < cooldownTicks) {
                return false;
            }
            return true;
        }

        void recordReaction(UUID playerId, UUID emoteId, long gameTime) {
            playerState.put(playerId, new PlayerReactionState(emoteId, gameTime));
        }
    }

    private record PlayerReactionState(UUID lastEmoteId, long lastReactionTick) {
    }
}
