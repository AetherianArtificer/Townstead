package com.aetherianartificer.townstead.client.animation.nativeclip;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.animation.emote.EmoteEasing;
import com.aetherianartificer.townstead.client.animation.emote.ParsedBoneAnimation;
import com.aetherianartificer.townstead.client.animation.emote.ParsedEmote;
import com.aetherianartificer.townstead.client.animation.emote.ParsedKeyframe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Resource-pack-owned native humanoid clips; parsing and playback require no optional mod. */
public final class NativeClipRegistry {
    public static final String SCHEMA = "townstead_performance:native_clip/v1";
    private static final String FAMILY = "townstead_performance";
    private static volatile Map<ResourceLocation, ParsedEmote> clips = Map.of();
    private static volatile Map<ResourceLocation, BedrockPerformanceClip> bedrockClips = Map.of();

    private NativeClipRegistry() {}

    public static Optional<ParsedEmote> get(ResourceLocation id) { return Optional.ofNullable(clips.get(id)); }
    public static Optional<BedrockPerformanceClip> getBedrock(ResourceLocation id) { return Optional.ofNullable(bedrockClips.get(id)); }
    public static boolean contains(ResourceLocation id) { return clips.containsKey(id) || bedrockClips.containsKey(id); }
    public static int size() { return clips.size() + bedrockClips.size(); }

    public static void reload(ResourceManager manager) {
        Map<ResourceLocation, ParsedEmote> next = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager
                .listResources(FAMILY, id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath();
            ResourceLocation id = id(file.getNamespace(), path.substring(FAMILY.length() + 1, path.length() - 5));
            try (Reader reader = entry.getValue().openAsReader()) {
                next.put(id, parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                Townstead.LOGGER.warn("Native performance clip {} rejected: {}", file, exception.getMessage());
            }
        }
        clips = Map.copyOf(next);
        Map<ResourceLocation, BedrockPerformanceClip> bedrock = new LinkedHashMap<>();
        // Standard Blockbench Bedrock exports, kept separate from legacy native_clip/v1 resources.
        for (var entry : manager.listResources("animations", file -> file.getPath().startsWith("animations/townstead/")
                && file.getPath().endsWith(".animation.json")).entrySet()) {
            try (Reader reader = entry.getValue().openAsReader()) {
                Map<ResourceLocation, BedrockPerformanceClip> fileClips = new LinkedHashMap<>();
                for (var animation : BedrockPerformanceClip.parse(JsonParser.parseReader(reader).getAsJsonObject()).entrySet()) {
                    ResourceLocation clipId = bedrockId(entry.getKey().getNamespace(), animation.getKey());
                    if (bedrock.containsKey(clipId) || fileClips.containsKey(clipId))
                        throw new IllegalArgumentException("Duplicate animation id " + clipId);
                    fileClips.put(clipId, animation.getValue());
                }
                bedrock.putAll(fileClips);
            } catch (Exception exception) {
                Townstead.LOGGER.warn("Blockbench performance file {} rejected: {}", entry.getKey(), exception.getMessage());
            }
        }
        bedrockClips = Map.copyOf(bedrock);
        Townstead.LOGGER.info("[AnimationBridge] loaded {} legacy and {} Blockbench performance clips", clips.size(), bedrockClips.size());
    }

    /** animation.pack.wave -> resource namespace:pack.wave; preserve the full name to avoid collisions. */
    public static ResourceLocation bedrockId(String namespace, String name) {
        String path = name.startsWith("animation.") ? name.substring("animation.".length()) : name;
        if (path.isEmpty()) throw new IllegalArgumentException("Empty animation name");
        return id(namespace, path);
    }

    public static ParsedEmote parse(ResourceLocation id, JsonObject json) {
        if (!SCHEMA.equals(GsonHelper.getAsString(json, "schema", ""))) {
            throw new IllegalArgumentException("schema must be " + SCHEMA);
        }
        int duration = GsonHelper.getAsInt(json, "duration_ticks", 20);
        if (duration < 1 || duration > 1200) throw new IllegalArgumentException("duration_ticks must be 1..1200");
        boolean loop = GsonHelper.getAsBoolean(json, "loop", false);
        JsonObject bones = GsonHelper.getAsJsonObject(json, "bones");
        Map<String, ParsedBoneAnimation> parsed = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : bones.entrySet()) {
            if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException("bone must be an object");
            parsed.put(entry.getKey(), bone(entry.getValue().getAsJsonObject(), duration));
        }
        if (parsed.isEmpty()) throw new IllegalArgumentException("bones must not be empty");
        return new ParsedEmote(id, id.toString(), 0, duration, duration, 0,
                loop ? ParsedEmote.LoopType.LOOP : ParsedEmote.LoopType.PLAY_ONCE,
                true, false, Map.copyOf(parsed));
    }

    private static ParsedBoneAnimation bone(JsonObject json, int duration) {
        Axes rotation = axes(json, "rotation", duration, true);
        Axes position = axes(json, "position", duration, false);
        Axes scale = axes(json, "scale", duration, false);
        List<ParsedKeyframe> bend = new ArrayList<>(), direction = new ArrayList<>();
        if (json.has("bend")) for (JsonElement element : json.getAsJsonArray("bend")) {
            JsonObject frame = element.getAsJsonObject();
            int tick = checkedTick(frame, duration);
            JsonArray value = GsonHelper.getAsJsonArray(frame, "value");
            bend.add(new ParsedKeyframe(tick, radians(value.get(0).getAsFloat()), easing(frame)));
            direction.add(new ParsedKeyframe(tick, radians(value.get(1).getAsFloat()), easing(frame)));
        }
        return new ParsedBoneAnimation(position.x, 0, position.y, 0, position.z, 0,
                rotation.x, 0, rotation.y, 0, rotation.z, 0,
                scale.x, 1, scale.y, 1, scale.z, 1,
                bend, 0, direction, 0,
                !position.x.isEmpty(), !scale.x.isEmpty(), !bend.isEmpty());
    }

    private static Axes axes(JsonObject bone, String key, int duration, boolean degrees) {
        List<ParsedKeyframe> x = new ArrayList<>(), y = new ArrayList<>(), z = new ArrayList<>();
        if (!bone.has(key)) return new Axes(x, y, z);
        for (JsonElement element : bone.getAsJsonArray(key)) {
            JsonObject frame = element.getAsJsonObject();
            int tick = checkedTick(frame, duration);
            JsonArray value = GsonHelper.getAsJsonArray(frame, "value");
            if (value.size() != 3) throw new IllegalArgumentException(key + " value must have three axes");
            EmoteEasing easing = easing(frame);
            float a = value.get(0).getAsFloat(), b = value.get(1).getAsFloat(), c = value.get(2).getAsFloat();
            x.add(new ParsedKeyframe(tick, degrees ? radians(a) : a, easing));
            y.add(new ParsedKeyframe(tick, degrees ? radians(b) : b, easing));
            z.add(new ParsedKeyframe(tick, degrees ? radians(c) : c, easing));
        }
        return new Axes(x, y, z);
    }

    private static int checkedTick(JsonObject frame, int duration) {
        int tick = GsonHelper.getAsInt(frame, "tick");
        if (tick < 0 || tick > duration) throw new IllegalArgumentException("keyframe tick outside clip duration");
        return tick;
    }

    private static EmoteEasing easing(JsonObject frame) {
        return EmoteEasing.fromNameOrLinear(GsonHelper.getAsString(frame, "easing", "inoutsine"));
    }

    private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    private record Axes(List<ParsedKeyframe> x, List<ParsedKeyframe> y, List<ParsedKeyframe> z) {}

    private static ResourceLocation id(String namespace, String path) {
        //? if neoforge {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
        //?} else {
        /*return new ResourceLocation(namespace, path);
        *///?}
    }
}
