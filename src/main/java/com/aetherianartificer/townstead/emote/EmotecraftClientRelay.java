package com.aetherianartificer.townstead.emote;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.client.Minecraft;
//? if neoforge {
import net.neoforged.neoforge.network.PacketDistributor;
//?}

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EmotecraftClientRelay {
    private static final Pattern TRANSLATE_KEY = Pattern.compile("emotecraft\\.emote\\.([a-z0-9_]+)\\.name", Pattern.CASE_INSENSITIVE);
    private static final Pattern TEXT_VALUE = Pattern.compile("\"text\"\\s*:\\s*\"([^\"]+)\"");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private EmotecraftClientRelay() {}

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        try {
            ClassLoader loader = Townstead.class.getClassLoader();
            Class<?> eventsClass = Class.forName("io.github.kosmx.emotes.api.events.client.ClientEmoteEvents", false, loader);
            Class<?> eventClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.Event", false, loader);
            Class<?> listenerClass = Class.forName("io.github.kosmx.emotes.api.events.client.ClientEmoteEvents$EmotePlayEvent", false, loader);
            Method register = eventClass.getMethod("register", Object.class);

            Class<?> animationClass = resolveAnimationClass(loader);
            Method uuidMethod = resolveMethod(animationClass, "getUuid", "uuid", "get");
            Method nameMethod = findMethod(animationClass, "getName", "name");
            Field extraDataField = findField(animationClass, "extraData");

            Object playEvent = eventsClass.getField("EMOTE_PLAY").get(null);
            Object listener = Proxy.newProxyInstance(loader, new Class<?>[]{listenerClass}, (proxy, method, args) -> {
                if (!"onEmotePlay".equals(method.getName()) || args == null || args.length < 3 || !(args[2] instanceof UUID userId)) {
                    return null;
                }
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.player == null || !minecraft.player.getUUID().equals(userId)) {
                    return null;
                }
                ActivePlayerEmote emote = createActiveEmote(args[0], uuidMethod, nameMethod, extraDataField);
                if (emote == null) return null;
                Townstead.LOGGER.info("[EmoteDebug] Emotecraft client event relayed for user {} aliases={}", userId, emote.aliases());
                PacketDistributor.sendToServer(new PlayerEmoteRelayPayload("emotecraft", emote.uuid(), emote.aliases().stream().toList()));
                return null;
            });
            register.invoke(playEvent, listener);
            Townstead.LOGGER.info("[EmoteDebug] Registered Emotecraft client relay listeners");
        } catch (Throwable ex) {
            Townstead.LOGGER.warn("[EmoteDebug] Failed to register Emotecraft client relay: {}", ex.toString());
        }
    }

    public static void relayLocalAnimation(Object animation) {
        if (animation == null) return;
        try {
            ClassLoader loader = Townstead.class.getClassLoader();
            Class<?> animationClass = resolveAnimationClass(loader);
            if (!animationClass.isInstance(animation)) return;
            Method uuidMethod = resolveMethod(animationClass, "getUuid", "uuid", "get");
            Method nameMethod = findMethod(animationClass, "getName", "name");
            Field extraDataField = findField(animationClass, "extraData");

            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;

            ActivePlayerEmote emote = createActiveEmote(animation, uuidMethod, nameMethod, extraDataField);
            if (emote == null) return;

            Townstead.LOGGER.info("[EmoteDebug] Emotecraft mixin relay for user {} aliases={}", minecraft.player.getUUID(), emote.aliases());
            PacketDistributor.sendToServer(new PlayerEmoteRelayPayload("emotecraft", emote.uuid(), emote.aliases().stream().toList()));
        } catch (Throwable ex) {
            Townstead.LOGGER.warn("[EmoteDebug] Emotecraft mixin relay failed: {}", ex.toString());
        }
    }

    public static void relayCurrentLocalAnimation() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null) return;

            Method getEmote = minecraft.player.getClass().getMethod("emotecraft$getEmote");
            Object emotePlayer = getEmote.invoke(minecraft.player);
            if (emotePlayer == null) return;

            Method currentAnimation = findMethod(emotePlayer.getClass(), "getData", "getCurrentAnimationInstance");
            if (currentAnimation == null) {
                throw new NoSuchMethodException(emotePlayer.getClass().getName() + ".getData/getCurrentAnimationInstance");
            }
            relayLocalAnimation(currentAnimation.invoke(emotePlayer));
        } catch (Throwable ex) {
            Townstead.LOGGER.warn("[EmoteDebug] Emotecraft current-animation relay failed: {}", ex.toString());
        }
    }

    private static ActivePlayerEmote createActiveEmote(Object animation, Method uuidMethod, Method nameMethod, Field extraDataField) {
        if (animation == null) return null;
        try {
            UUID uuid = (UUID) uuidMethod.invoke(animation);
            if (uuid == null) return null;
            Set<String> aliases = new LinkedHashSet<>();
            aliases.add(uuid.toString().toLowerCase(Locale.ROOT));

            if (nameMethod != null) {
                extractAliases(nameMethod.invoke(animation), aliases);
            }

            if (extraDataField != null) {
                Object rawExtraData = extraDataField.get(animation);
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

    private static void extractAliases(Object rawValue, Set<String> aliases) {
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

    private static Class<?> resolveAnimationClass(ClassLoader loader) throws ClassNotFoundException {
        for (String name : new String[]{
                "dev.kosmx.playerAnim.core.data.KeyframeAnimation",
                "com.zigythebird.playeranimcore.animation.Animation"
        }) {
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException ignored) {
            }
        }
        throw new ClassNotFoundException("No supported Emotecraft animation class found");
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
}
