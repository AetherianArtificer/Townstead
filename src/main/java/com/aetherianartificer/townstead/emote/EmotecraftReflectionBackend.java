package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.compat.ModCompat;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmotecraftReflectionBackend implements PlayerEmoteEventSource, VillagerEmotePlaybackBackend {
    private static final Pattern TRANSLATE_KEY = Pattern.compile("emotecraft\\.emote\\.([a-z0-9_]+)\\.name", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_VALUE = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
    private static final long EVENT_CACHE_TTL_MS = 4000L;
    private static final Map<UUID, CachedPlayerEmote> RECENT_EMOTES = new ConcurrentHashMap<>();

    private volatile boolean resolved;
    private volatile boolean available;
    private Method getPlayedEmote;
    private Method pairLeft;
    private Method animationUuid;
    private Method animationName;
    private Field animationExtraData;
    private String failureReason = "unresolved";

    @Override
    public String providerId() {
        return "emotecraft";
    }

    @Override
    public void initialize() {
        resolve();
    }

    @Override
    public boolean isActive() {
        resolve();
        return available;
    }

    public ActivePlayerEmote getCachedActiveEmote(ServerPlayer player) {
        if (player == null) return null;
        resolve();
        if (!available) return null;
        CachedPlayerEmote cached = RECENT_EMOTES.get(player.getUUID());
        if (cached != null) {
            if ((System.currentTimeMillis() - cached.observedAtMs) <= EVENT_CACHE_TTL_MS) {
                return cached.emote;
            }
            RECENT_EMOTES.remove(player.getUUID(), cached);
        }
        try {
            Object pair = getPlayedEmote.invoke(null, player.getUUID());
            if (pair == null) return null;
            Object animation = pairLeft.invoke(pair);
            return createActiveEmote(animation);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    public boolean canPlayVillagerEmote() {
        return false;
    }

    @Override
    public boolean tryPlayVillagerEmote(VillagerEntityMCA villager, String emoteKey) {
        return false;
    }

    private void extractAliases(Object rawValue, Set<String> aliases) {
        if (rawValue == null) return;
        String text = String.valueOf(rawValue);
        if (text.isBlank()) return;

        Matcher translate = TRANSLATE_KEY.matcher(text);
        while (translate.find()) {
            aliases.add(translate.group(1).toLowerCase(Locale.ROOT));
        }

        Matcher plainText = TEXT_VALUE.matcher(text);
        while (plainText.find()) {
            String normalized = normalizeAlias(plainText.group(1));
            if (!normalized.isBlank()) aliases.add(normalized);
        }

        String normalized = normalizeAlias(text);
        if (!normalized.isBlank()) aliases.add(normalized);
    }

    private static String normalizeAlias(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+|_+$", "");
        return normalized;
    }

    private synchronized void resolve() {
        if (resolved) return;
        resolved = true;
        available = false;
        if (!ModCompat.isLoaded("emotecraft")) {
            failureReason = "Emotecraft mod not loaded";
            Townstead.LOGGER.info("[EmoteDebug] Emotecraft reflection backend inactive: {}", failureReason);
            return;
        }
        try {
            ClassLoader loader = Townstead.class.getClassLoader();
            Class<?> apiClass = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteAPI", false, loader);
            getPlayedEmote = apiClass.getMethod("getPlayedEmote", UUID.class);
            Class<?> pairClass = getPlayedEmote.getReturnType();
            pairLeft = resolveMethod(pairClass, "getLeft", "left", "first");

            Class<?> animationClass = resolveAnimationClass(loader);
            animationUuid = resolveMethod(animationClass, "getUuid", "uuid", "get");
            animationName = findMethod(animationClass, "getName", "name");
            animationExtraData = findField(animationClass, "extraData");
            registerEventListeners(loader, animationClass);
            available = true;
            failureReason = "";
            Townstead.LOGGER.info("[EmoteDebug] Emotecraft reflection backend active: pair={}, animation={}",
                    pairClass.getName(), animationClass.getName());
        } catch (Throwable ex) {
            available = false;
            failureReason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            Townstead.LOGGER.warn("[EmoteDebug] Emotecraft reflection backend failed to initialize: {}", failureReason, ex);
        }
    }

    private void registerEventListeners(ClassLoader loader, Class<?> animationClass) throws ReflectiveOperationException {
        Class<?> eventsClass = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteEvents", false, loader);
        Method eventRegister = Class.forName("dev.kosmx.playerAnim.core.impl.event.Event", false, loader)
                .getMethod("register", Object.class);

        Class<?> playListenerClass = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteEvents$EmotePlayEvent", false, loader);
        Object playEvent = eventsClass.getField("EMOTE_PLAY").get(null);
            Object playListener = Proxy.newProxyInstance(loader, new Class<?>[]{playListenerClass}, (proxy, method, args) -> {
                if ("onEmotePlay".equals(method.getName()) && args != null && args.length >= 3 && args[2] instanceof UUID userId) {
                    ActivePlayerEmote emote = createActiveEmote(args[0]);
                    if (emote != null) {
                        RECENT_EMOTES.put(userId, new CachedPlayerEmote(emote, System.currentTimeMillis()));
                        Townstead.LOGGER.info("[EmoteDebug] Emotecraft event received for user {} aliases={}", userId, emote.aliases());
                        ServerPlayer player = resolvePlayer(userId);
                        if (player != null) {
                            EmoteReactionDispatcher.dispatchPlayerEmote(providerId(), player, emote);
                        } else {
                            Townstead.LOGGER.info("[EmoteDebug] Emotecraft event received for {} but no ServerPlayer was found", userId);
                        }
                    }
                }
                return null;
            });
            eventRegister.invoke(playEvent, playListener);

        Class<?> stopListenerClass = Class.forName("io.github.kosmx.emotes.api.events.server.ServerEmoteEvents$EmoteStopEvent", false, loader);
        Object stopEvent = eventsClass.getField("EMOTE_STOP_BY_USER").get(null);
            Object stopListener = Proxy.newProxyInstance(loader, new Class<?>[]{stopListenerClass}, (proxy, method, args) -> {
                if ("onStopEmote".equals(method.getName()) && args != null && args.length >= 2 && args[1] instanceof UUID userId) {
                    RECENT_EMOTES.remove(userId);
                    Townstead.LOGGER.info("[EmoteDebug] Emotecraft stop event received for user {}", userId);
                }
                return null;
            });
            eventRegister.invoke(stopEvent, stopListener);
            Townstead.LOGGER.info("[EmoteDebug] Registered Emotecraft event listeners");
        }

    private ActivePlayerEmote createActiveEmote(Object animation) {
        if (animation == null) return null;
        try {
            UUID uuid = (UUID) animationUuid.invoke(animation);
            if (uuid == null) return null;

            Set<String> aliases = new LinkedHashSet<>();
            aliases.add(uuid.toString().toLowerCase(Locale.ROOT));

            if (animationName != null) {
                extractAliases(animationName.invoke(animation), aliases);
            }

            if (animationExtraData != null) {
                Object rawExtraData = animationExtraData.get(animation);
                if (rawExtraData instanceof Map<?, ?> extraData) {
                    extractAliases(extraData.get("name"), aliases);
                    extractAliases(extraData.get("fileName"), aliases);
                    extractAliases(extraData.get("filename"), aliases);
                    extractAliases(extraData.get("emote"), aliases);
                }
            }

            return new ActivePlayerEmote(uuid, Set.copyOf(aliases));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerPlayer resolvePlayer(UUID userId) {
        try {
            //? if neoforge {
            net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            //?} else if forge {
            /*net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();*/
            //?}
            return server == null ? null : server.getPlayerList().getPlayer(userId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Class<?> resolveAnimationClass(ClassLoader loader) throws ClassNotFoundException {
        Map<String, ClassNotFoundException> failures = new LinkedHashMap<>();
        for (String name : new String[]{
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation",
                "com.zigythebird.playeranimcore.animation.Animation"
        }) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ex) {
                failures.put(name, ex);
            }
        }
        ClassNotFoundException failure = failures.values().iterator().next();
        for (Map.Entry<String, ClassNotFoundException> entry : failures.entrySet()) {
            failure.addSuppressed(new ClassNotFoundException(entry.getKey(), entry.getValue()));
        }
        throw failure;
    }

    private static Method resolveMethod(Class<?> type, String... names) throws NoSuchMethodException {
        Method method = findMethod(type, names);
        if (method == null) {
            throw new NoSuchMethodException(type.getName() + " missing any of " + String.join(", ", names));
        }
        return method;
    }

    private static Method findMethod(Class<?> type, String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        }
    }

    private record CachedPlayerEmote(ActivePlayerEmote emote, long observedAtMs) {
    }
}
