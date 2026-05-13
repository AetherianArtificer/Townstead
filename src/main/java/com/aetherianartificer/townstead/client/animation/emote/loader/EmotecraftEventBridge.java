package com.aetherianartificer.townstead.client.animation.emote.loader;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmotePlaybackRegistry;
import com.aetherianartificer.townstead.client.animation.emote.EmoteRegistry;
import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import com.aetherianartificer.townstead.client.animation.emote.TownsteadEmoteApi;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.UUID;

/**
 * Bridges Emotecraft's client-side emote events into Townstead's playback registry.
 * When the user opens the Emotecraft menu (default {@code B}) and plays an emote,
 * Emotecraft fires {@code ClientEmoteEvents.EMOTE_PLAY}; we listen, parse the
 * underlying {@code KeyframeAnimation} into a {@link ParsedEmote}, register it as
 * a transient entry, and trigger our own per-bone-takeover playback on the player.
 *
 * <p>Without this, emotes from the B menu run inside Emotecraft's own animation
 * runtime and never touch Townstead's bridge — so on MCA-rendered players the
 * emote either fights with CEM/Fresh-Animations or doesn't apply at all.</p>
 */
public final class EmotecraftEventBridge {
    private static volatile boolean registered;

    private EmotecraftEventBridge() {}

    public static synchronized void ensureRegistered() {
        if (registered) return;
        if (!EmoteReflection.isAvailable()) return;
        try {
            Object playEvent = EmoteReflection.emotePlayEventField.get(null);
            Object stopEvent = EmoteReflection.emoteStopEventField.get(null);

            Object playProxy = Proxy.newProxyInstance(
                    EmoteReflection.emotePlayEventInterface.getClassLoader(),
                    new Class<?>[]{EmoteReflection.emotePlayEventInterface},
                    new PlayHandler());
            Object stopProxy = Proxy.newProxyInstance(
                    EmoteReflection.emoteStopEventInterface.getClassLoader(),
                    new Class<?>[]{EmoteReflection.emoteStopEventInterface},
                    new StopHandler());

            EmoteReflection.playerAnimEventRegister.invoke(playEvent, playProxy);
            EmoteReflection.playerAnimEventRegister.invoke(stopEvent, stopProxy);

            registered = true;
            Townstead.LOGGER.info("[AnimationBridge] Emotecraft event bridge registered");
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[AnimationBridge] failed to bind Emotecraft event listeners ({})",
                    t.getMessage());
        }
    }

    public static synchronized void invalidate() {
        registered = false;
    }

    private static class PlayHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (!"onEmotePlay".equals(method.getName()) || args == null || args.length < 2) return null;
            Object animation = args[0];
            UUID playerUuid = (UUID) args[1];
            if (animation == null || playerUuid == null) return null;
            handlePlay(animation, playerUuid);
            return null;
        }
    }

    private static class StopHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args) {
            if (!"onEmoteStop".equals(method.getName()) || args == null || args.length < 2) return null;
            UUID playerUuid = (UUID) args[1];
            if (playerUuid != null) handleStop(playerUuid);
            return null;
        }
    }

    private static void handlePlay(Object keyframeAnimation, UUID playerUuid) {
        try {
            UUID animUuid = null;
            if (EmoteReflection.animGetUuid != null) {
                Object u = EmoteReflection.animGetUuid.invoke(keyframeAnimation);
                if (u instanceof UUID uu) animUuid = uu;
            }
            String suffix = animUuid != null ? animUuid.toString() : Long.toHexString(System.nanoTime());
            ResourceLocation id = synthId(suffix);

            ParsedEmote parsed = EmotecraftEmoteLoader.parseAnimation(id, keyframeAnimation);
            if (parsed == null) {
                Townstead.LOGGER.info("[AnimationBridge] EMOTE_PLAY parsed no mappable bones player={} emoteUuid={}",
                        playerUuid, animUuid);
                return;
            }
            EmoteRegistry.putTransient(parsed);

            Player player = lookupPlayer(playerUuid);
            if (player == null) {
                Townstead.LOGGER.info("[AnimationBridge] EMOTE_PLAY player not found player={} emote={} bones={}",
                        playerUuid, id, parsed.bones().keySet());
                return;
            }
            Townstead.LOGGER.info("[AnimationBridge] EMOTE_PLAY bridged player={} emote={} loop={} bones={}",
                    playerUuid, id, parsed.loopType(), parsed.bones().keySet());
            TownsteadEmoteApi.trigger(player, id, parsed.loopType(), 1.0F);
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[AnimationBridge] EMOTE_PLAY bridge failed ({})", t.getMessage());
        }
    }

    private static void handleStop(UUID playerUuid) {
        Player player = lookupPlayer(playerUuid);
        if (player != null) {
            TownsteadEmoteApi.stop(player);
        } else {
            EmotePlaybackRegistry.remove(playerUuid);
        }
    }

    private static Player lookupPlayer(UUID uuid) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null) return null;
        return client.level.getPlayerByUUID(uuid);
    }

    private static ResourceLocation synthId(String suffix) {
        String safe = suffix.replaceAll("[^a-z0-9_.-]", "_").toLowerCase(java.util.Locale.ROOT);
        String path = "emotecraft_runtime/" + safe;
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, path);
        //?} else {
        /*return new ResourceLocation(Townstead.MOD_ID, path);
        *///?}
    }
}
