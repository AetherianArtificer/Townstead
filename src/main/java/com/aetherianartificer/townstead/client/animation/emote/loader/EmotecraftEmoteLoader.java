package com.aetherianartificer.townstead.client.animation.emote.loader;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteBoneMapping;
import com.aetherianartificer.townstead.client.animation.emote.EmoteEasing;
import com.aetherianartificer.townstead.client.animation.emote.ParsedBoneAnimation;
import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import com.aetherianartificer.townstead.client.animation.emote.ParsedKeyframe;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code .json} / {@code .emotecraft} files into Townstead's source-neutral
 * {@link ParsedEmote} via reflective calls into Emotecraft's {@code
 * UniversalEmoteSerializer} and the bundled {@code dev.kosmx.playerAnim} library's
 * {@code KeyframeAnimation}. All reflection lives behind {@link EmoteReflection};
 * once parsing finishes, no Emotecraft references are retained.
 */
public final class EmotecraftEmoteLoader {
    private EmotecraftEmoteLoader() {}

    public static List<ParsedEmote> load(ResourceLocation baseId, InputStream stream, String fileName) {
        if (!EmoteReflection.isAvailable()) return Collections.emptyList();

        try {
            List<Object> animations = EmoteReflection.invokeReadData(stream, fileName);
            if (animations == null || animations.isEmpty()) return Collections.emptyList();

            List<ParsedEmote> out = new ArrayList<>(animations.size());
            int variant = 0;
            for (Object animation : animations) {
                ResourceLocation id;
                if (animations.size() == 1) {
                    id = baseId;
                } else {
                    String suffixedPath = baseId.getPath() + "_" + (variant++);
                    //? if neoforge {
                    id = ResourceLocation.fromNamespaceAndPath(baseId.getNamespace(), suffixedPath);
                    //?} else {
                    /*id = new ResourceLocation(baseId.getNamespace(), suffixedPath);
                    *///?}
                }
                ParsedEmote parsed = parseAnimation(id, animation);
                if (parsed != null) out.add(parsed);
            }
            return out;
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[AnimationBridge] emote loader: failed to parse {} ({})",
                    fileName, t.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parse an already-instantiated {@code KeyframeAnimation} (e.g. one received via
     * Emotecraft's {@code EMOTE_PLAY} event) into a {@link ParsedEmote}. Returns
     * {@code null} if the animation has no Townstead-mappable bones or reflection
     * fails partway through.
     */
    public static ParsedEmote parseAnimation(ResourceLocation id, Object animation) {
        try {
            int beginTick = EmoteReflection.animBeginTick.getInt(animation);
            int endTick = EmoteReflection.animEndTick.getInt(animation);
            int stopTick = EmoteReflection.animStopTick.getInt(animation);
            int returnToTick = EmoteReflection.animReturnToTick.getInt(animation);
            boolean isInfinite = EmoteReflection.animIsInfinite.getBoolean(animation);

            String displayName = id.getPath();
            try {
                Object name = EmoteReflection.animGetName.invoke(animation);
                if (name instanceof String s && !s.isBlank()) displayName = s;
            } catch (Throwable ignored) {
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> bodyParts = (Map<String, Object>)
                    EmoteReflection.animGetBodyParts.invoke(animation);
            if (bodyParts == null || bodyParts.isEmpty()) return null;

            Map<String, ParsedBoneAnimation> mapped = new HashMap<>();
            for (Map.Entry<String, Object> e : bodyParts.entrySet()) {
                String key = normalizeKey(e.getKey());
                if (key == null) continue;
                if (EmoteBoneMapping.mapTargets(key).isEmpty()) continue;
                ParsedBoneAnimation pba = parseStateCollection(e.getValue());
                if (pba == null || !pba.hasAnyKeyframes()) continue;
                mapped.put(key, pba);
            }
            if (mapped.isEmpty()) return null;

            ParsedEmote.LoopType loop = isInfinite ? ParsedEmote.LoopType.LOOP : ParsedEmote.LoopType.PLAY_ONCE;
            return new ParsedEmote(id, displayName, beginTick, endTick, stopTick, returnToTick, loop, mapped);
        } catch (Throwable t) {
            Townstead.LOGGER.debug("[AnimationBridge] emote loader: animation parse failed for {} ({})",
                    id, t.getMessage());
            return null;
        }
    }

    private static ParsedBoneAnimation parseStateCollection(Object stateCollection) throws Exception {
        ParsedAxis x = parseState(stateCollection, EmoteReflection.scX, 0F);
        ParsedAxis y = parseState(stateCollection, EmoteReflection.scY, 0F);
        ParsedAxis z = parseState(stateCollection, EmoteReflection.scZ, 0F);
        ParsedAxis pitch = parseState(stateCollection, EmoteReflection.scPitch, 0F);
        ParsedAxis yaw = parseState(stateCollection, EmoteReflection.scYaw, 0F);
        ParsedAxis roll = parseState(stateCollection, EmoteReflection.scRoll, 0F);
        ParsedAxis scaleX = parseState(stateCollection, EmoteReflection.scScaleX, 1F);
        ParsedAxis scaleY = parseState(stateCollection, EmoteReflection.scScaleY, 1F);
        ParsedAxis scaleZ = parseState(stateCollection, EmoteReflection.scScaleZ, 1F);

        boolean translationKeyed = !x.keyframes.isEmpty() || !y.keyframes.isEmpty() || !z.keyframes.isEmpty();
        boolean scaleKeyed = !scaleX.keyframes.isEmpty() || !scaleY.keyframes.isEmpty() || !scaleZ.keyframes.isEmpty();

        return new ParsedBoneAnimation(
                x.keyframes, x.defaultValue,
                y.keyframes, y.defaultValue,
                z.keyframes, z.defaultValue,
                pitch.keyframes, pitch.defaultValue,
                yaw.keyframes, yaw.defaultValue,
                roll.keyframes, roll.defaultValue,
                scaleX.keyframes, scaleX.defaultValue,
                scaleY.keyframes, scaleY.defaultValue,
                scaleZ.keyframes, scaleZ.defaultValue,
                translationKeyed,
                scaleKeyed
        );
    }

    private record ParsedAxis(List<ParsedKeyframe> keyframes, float defaultValue) {
        static ParsedAxis empty(float defaultValue) {
            return new ParsedAxis(Collections.emptyList(), defaultValue);
        }
    }

    private static ParsedAxis parseState(Object stateCollection, Field field, float defaultIfMissing) {
        if (stateCollection == null || field == null) return ParsedAxis.empty(defaultIfMissing);
        try {
            Object state = field.get(stateCollection);
            if (state == null) return ParsedAxis.empty(defaultIfMissing);

            if (EmoteReflection.stateIsEnabled != null) {
                Object enabled = EmoteReflection.stateIsEnabled.invoke(state);
                if (Boolean.FALSE.equals(enabled)) {
                    return ParsedAxis.empty(EmoteReflection.stateDefaultValue.getFloat(state));
                }
            }

            float defaultValue = EmoteReflection.stateDefaultValue.getFloat(state);
            Object kfList = EmoteReflection.stateGetKeyFrames.invoke(state);
            if (!(kfList instanceof List<?> list) || list.isEmpty()) {
                return new ParsedAxis(Collections.emptyList(), defaultValue);
            }

            List<ParsedKeyframe> out = new ArrayList<>(list.size());
            for (Object kf : list) {
                ParsedKeyframe parsed = parseKeyframe(kf);
                if (parsed != null) out.add(parsed);
            }
            return new ParsedAxis(out, defaultValue);
        } catch (Throwable t) {
            return ParsedAxis.empty(defaultIfMissing);
        }
    }

    private static ParsedKeyframe parseKeyframe(Object kf) {
        try {
            int tick = EmoteReflection.kfTick.getInt(kf);
            float value = EmoteReflection.kfValue.getFloat(kf);
            Object ease = EmoteReflection.kfEase.get(kf);
            EmoteEasing easing = EmoteEasing.fromNameOrLinear(ease == null ? null : ease.toString());
            return new ParsedKeyframe(tick, value, easing);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String normalizeKey(String raw) {
        if (raw == null) return null;
        String key = raw.trim().toLowerCase(java.util.Locale.ROOT).replace("_", "");
        return key.isEmpty() ? null : key;
    }
}
