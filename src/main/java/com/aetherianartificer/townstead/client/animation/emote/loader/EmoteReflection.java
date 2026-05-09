package com.aetherianartificer.townstead.client.animation.emote.loader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Single source of reflective contact with Emotecraft + the {@code dev.kosmx.playerAnim}
 * library it embeds. All class and method handles are resolved once on first access
 * (or after {@link #invalidate()}); failures set {@link #ok} to false and the loader
 * silently disables itself, mirroring how {@code EmfAnimationSourceAdapter} treats
 * EMF when absent — no info-level chatter when the mod just isn't installed.
 *
 * <p>Targets the playerAnim 2.x API (used by Emotecraft 2.4.x). Method-name lookups
 * try a couple of common conventions so minor upstream renames don't break us.</p>
 */
public final class EmoteReflection {
    private static volatile boolean attempted;
    private static volatile boolean ok;

    static Method readData;             // (InputStream, String) -> List<KeyframeAnimation>

    static Class<?> animationClass;     // dev.kosmx.playerAnim.core.data.KeyframeAnimation
    static Method animGetBodyParts;     // -> Map<String, StateCollection>
    static Method animGetName;          // -> String
    static Method animGetUuid;          // -> UUID
    static Field animBeginTick;
    static Field animEndTick;
    static Field animStopTick;
    static Field animReturnToTick;
    static Field animIsInfinite;
    static Field animExtraData;         // -> HashMap<String, Object>; "iconData" key holds a ByteBuffer of PNG bytes

    static Class<?> stateCollectionClass;
    static Field scX, scY, scZ;
    static Field scPitch, scYaw, scRoll;
    static Field scScaleX, scScaleY, scScaleZ;

    static Class<?> stateClass;
    static Method stateGetKeyFrames;    // -> List<KeyFrame>
    static Field stateDefaultValue;
    static Method stateIsEnabled;

    static Class<?> keyframeClass;
    static Field kfTick;
    static Field kfValue;
    static Field kfEase;

    static Class<?> clientEmoteEventsClass;
    static Field emotePlayEventField;
    static Field emoteStopEventField;
    static Class<?> emotePlayEventInterface;
    static Class<?> emoteStopEventInterface;
    static Class<?> playerAnimEventClass;
    static Method playerAnimEventRegister;

    static Class<?> clientEmoteApiClass;
    static Method clientEmoteApiList;

    private EmoteReflection() {}

    public static synchronized boolean isAvailable() {
        if (!attempted) tryResolve();
        return ok;
    }

    public static synchronized void invalidate() {
        attempted = false;
        ok = false;
    }

    @SuppressWarnings("unchecked")
    static List<Object> invokeReadData(InputStream stream, String fileName) throws Throwable {
        return (List<Object>) readData.invoke(null, stream, fileName);
    }

    private static void tryResolve() {
        attempted = true;
        ok = false;
        try {
            Class<?> serializerClass = Class.forName(
                    "io.github.kosmx.emotes.server.serializer.UniversalEmoteSerializer");
            readData = serializerClass.getMethod("readData", InputStream.class, String.class);

            animationClass = Class.forName("dev.kosmx.playerAnim.core.data.KeyframeAnimation");
            animGetBodyParts = animationClass.getMethod("getBodyParts");
            animGetName = animationClass.getMethod("getName");
            animGetUuid = anyMethodOrNull(animationClass, "getUuid", "getUUID");
            animBeginTick = animationClass.getField("beginTick");
            animEndTick = animationClass.getField("endTick");
            animStopTick = animationClass.getField("stopTick");
            animReturnToTick = animationClass.getField("returnToTick");
            animIsInfinite = animationClass.getField("isInfinite");
            animExtraData = anyFieldOrNull(animationClass, "extraData");

            stateCollectionClass = Class.forName(
                    "dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection");
            scX = stateCollectionClass.getField("x");
            scY = stateCollectionClass.getField("y");
            scZ = stateCollectionClass.getField("z");
            scPitch = stateCollectionClass.getField("pitch");
            scYaw = stateCollectionClass.getField("yaw");
            scRoll = stateCollectionClass.getField("roll");
            scScaleX = anyFieldOrNull(stateCollectionClass, "scaleX");
            scScaleY = anyFieldOrNull(stateCollectionClass, "scaleY");
            scScaleZ = anyFieldOrNull(stateCollectionClass, "scaleZ");

            stateClass = Class.forName(
                    "dev.kosmx.playerAnim.core.data.KeyframeAnimation$StateCollection$State");
            stateGetKeyFrames = stateClass.getMethod("getKeyFrames");
            stateDefaultValue = stateClass.getField("defaultValue");
            stateIsEnabled = anyMethodOrNull(stateClass, "isEnabled");

            keyframeClass = Class.forName(
                    "dev.kosmx.playerAnim.core.data.KeyframeAnimation$KeyFrame");
            kfTick = keyframeClass.getField("tick");
            kfValue = keyframeClass.getField("value");
            kfEase = keyframeClass.getField("ease");

            clientEmoteEventsClass = Class.forName(
                    "io.github.kosmx.emotes.api.events.client.ClientEmoteEvents");
            emotePlayEventField = clientEmoteEventsClass.getField("EMOTE_PLAY");
            emoteStopEventField = clientEmoteEventsClass.getField("EMOTE_STOP");
            emotePlayEventInterface = Class.forName(
                    "io.github.kosmx.emotes.api.events.client.ClientEmoteEvents$EmotePlayEvent");
            emoteStopEventInterface = Class.forName(
                    "io.github.kosmx.emotes.api.events.client.ClientEmoteEvents$EmoteStopEvent");
            playerAnimEventClass = Class.forName("dev.kosmx.playerAnim.core.impl.event.Event");
            playerAnimEventRegister = playerAnimEventClass.getMethod("register", Object.class);

            clientEmoteApiClass = Class.forName(
                    "io.github.kosmx.emotes.api.events.client.ClientEmoteAPI");
            clientEmoteApiList = clientEmoteApiClass.getMethod("clientEmoteList");

            ok = true;
        } catch (Throwable ignored) {
            ok = false;
        }
    }

    private static Method anyMethodOrNull(Class<?> owner, String... candidates) {
        for (String name : candidates) {
            try {
                Method m = owner.getMethod(name);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    private static Field anyFieldOrNull(Class<?> owner, String... candidates) {
        for (String name : candidates) {
            try {
                Field f = owner.getField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
