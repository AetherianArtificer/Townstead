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
    static Field animExtraData;         // -> HashMap<String, Object>; holds JSON top-level fields like "name", "description", and "iconData" (ByteBuffer)

    static Class<?> stateCollectionClass;
    static Field scX, scY, scZ;
    static Field scPitch, scYaw, scRoll;
    static Field scScaleX, scScaleY, scScaleZ;
    static Field scBend;                          // bend angle keyframes
    static Field scBendDirection;                 // bend axis angle keyframes
    static Field scIsBendable;                    // boolean, true for arms/legs

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

    static Field emoteInstanceConfig;              // io.github.kosmx.emotes.executor.EmoteInstance.config (static, SerializableConfig)
    static Class<?> clientConfigClass;             // io.github.kosmx.emotes.main.config.ClientConfig
    static Field clientConfigFastMenuEmotes;       // -> UUID[][] (pages × 8 slots)

    static Field bendHelperInstance;               // dev.kosmx.playerAnim.impl.animation.IBendHelper.INSTANCE (static)
    static Method bendHelperBend;                  // void bend(ModelPart, float angle, float bendDirection)
    static Method bendHelperInit;                  // void initBend(ModelPart, Direction)
    static Object directionUp;                     // Direction.UP for re-init

    private EmoteReflection() {}

    public static synchronized boolean isAvailable() {
        if (!attempted) tryResolve();
        return ok;
    }

    public static synchronized void invalidate() {
        attempted = false;
        ok = false;
    }

    /**
     * Reflectively invokes playerAnim's {@code
     * IBendHelper.INSTANCE.bend(ModelPart, float, float)}. No-op when
     * playerAnim isn't loaded (or reflection couldn't resolve it).
     */
    /**
     * Calls {@code IBendHelper.INSTANCE.bend(part, bendAxis, bendAngle)}.
     * <b>Parameter order is intentional and matches the upstream API</b>: see
     * {@link com.aetherianartificer.townstead.client.animation.McaModelPartApplier}
     * for the rationale (BendHelper's internal clear-check reads the second
     * arg as the bend angle).
     *
     * <p>We call {@code initBend} on every invocation rather than caching the
     * first-sight call. {@code BipedEntityModelMixin} attaches mutators to
     * vanilla HumanoidModel children at model construction; MCA's wear layers
     * ({@code leftArmwear}, etc.) and other extras are NOT covered, so we
     * always re-attach. {@code initBend} is idempotent: re-attaching a
     * mutator on a part that already has one is fast and simply replaces the
     * cuboid wrappers — and crucially, it cannot leave the part with no
     * mutator (which is what a cached "first sight" call could fail at,
     * silently leaving the part forever un-bendable).</p>
     */
    /**
     * Attaches playerAnim's bend mutator to a ModelPart by calling
     * {@code IBendHelper.INSTANCE.initBend(part, Direction.UP)}. Idempotent.
     * Crucially, this also flips the part's bendylib {@code
     * hasMutatedCuboid} flag (via {@code getCuboids()} called inside
     * {@code optionalGetCuboid}), so bendylib's render redirect fires when
     * the part is drawn — without that flag, even a populated bend mutator
     * goes unused at render time.
     */
    public static void attachBendMutator(Object modelPart) {
        if (bendHelperInstance == null || bendHelperInit == null || directionUp == null) return;
        try {
            Object instance = bendHelperInstance.get(null);
            if (instance == null) return;
            bendHelperInit.invoke(instance, modelPart, directionUp);
        } catch (Throwable ignored) {
        }
    }

    public static void applyBend(Object modelPart, float bendAxis, float bendAngle) {
        if (bendHelperInstance == null || bendHelperBend == null) return;
        try {
            Object instance = bendHelperInstance.get(null);
            if (instance == null) return;
            if (bendHelperInit != null && directionUp != null) {
                try {
                    bendHelperInit.invoke(instance, modelPart, directionUp);
                } catch (Throwable ignored) {
                }
            }
            bendHelperBend.invoke(instance, modelPart, bendAxis, bendAngle);
        } catch (Throwable ignored) {
            // Single-frame failure — skip and let the next frame retry.
        }
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
            scBend = anyFieldOrNull(stateCollectionClass, "bend");
            scBendDirection = anyFieldOrNull(stateCollectionClass, "bendDirection");
            scIsBendable = anyFieldOrNull(stateCollectionClass, "isBendable");

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

            // Player-slot config is OPTIONAL — failure here must not disable
            // the whole loader, just leave the fast-menu lookup unavailable.
            try {
                Class<?> emoteInstanceClass = Class.forName(
                        "io.github.kosmx.emotes.executor.EmoteInstance");
                emoteInstanceConfig = emoteInstanceClass.getField("config");
                clientConfigClass = Class.forName(
                        "io.github.kosmx.emotes.main.config.ClientConfig");
                clientConfigFastMenuEmotes = clientConfigClass.getField("fastMenuEmotes");
            } catch (Throwable ignored) {
                emoteInstanceConfig = null;
                clientConfigFastMenuEmotes = null;
            }

            // playerAnim's IBendHelper — bundled with Emotecraft for arm/leg
            // bending. Optional; if it's missing, the bridge just skips bend
            // application and limbs render straight.
            try {
                Class<?> bendHelperClass = Class.forName(
                        "dev.kosmx.playerAnim.impl.animation.IBendHelper");
                bendHelperInstance = bendHelperClass.getField("INSTANCE");
                Class<?> modelPartClass = Class.forName(
                        "net.minecraft.client.model.geom.ModelPart");
                bendHelperBend = bendHelperClass.getMethod(
                        "bend", modelPartClass, float.class, float.class);
                Class<?> directionClass = Class.forName("net.minecraft.core.Direction");
                bendHelperInit = bendHelperClass.getMethod(
                        "initBend", modelPartClass, directionClass);
                directionUp = directionClass.getField("UP").get(null);
            } catch (Throwable ignored) {
                bendHelperInstance = null;
                bendHelperBend = null;
                bendHelperInit = null;
                directionUp = null;
            }

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
