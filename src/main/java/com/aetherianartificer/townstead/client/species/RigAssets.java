package com.aetherianartificer.townstead.client.species;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.AttachmentClient;
import com.aetherianartificer.townstead.data.DataPackLang;
import com.aetherianartificer.townstead.root.attachment.AttachmentAnimation;
import com.aetherianartificer.townstead.root.attachment.BbmodelConverter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the files a rig names (geometry, animations, textures) by logical id. A file the data
 * pack ships under that id wins and arrives over the blob sync; any other id is read from the
 * client's assets, i.e. the installed mods' own jars and resource packs. So a pack can point a rig
 * at another mod's model ({@code ribbits:geo/nitwit_ribbit.geo.json}) without copying it.
 */
public final class RigAssets {

    private static final Map<String, Optional<JsonObject>> GEO = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Map<String, AttachmentAnimation.Clip>>> ANIMATIONS =
            new ConcurrentHashMap<>();

    private RigAssets() {}

    /** Drop everything read from client assets (resource reload, new manifest). */
    public static void clear() {
        GEO.clear();
        ANIMATIONS.clear();
        RigModels.invalidate();
    }

    /** The geometry JSON for an id, or null while a pack blob is in flight or when nothing provides it. */
    @Nullable
    public static JsonObject geometry(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        if (AttachmentClient.hasNamedGeo(ref)) return AttachmentClient.namedGeoJson(ref);
        return GEO.computeIfAbsent(ref, RigAssets::readGeometry).orElse(null);
    }

    /** Whether the id is a pack geometry whose blob has not arrived yet (so a caller retries, not skips). */
    public static boolean geometryPending(String ref) {
        return ref != null && AttachmentClient.hasNamedGeo(ref) && AttachmentClient.namedGeoJson(ref) == null;
    }

    /** Every clip of an animation file, or null while a pack blob is in flight or when nothing provides it. */
    @Nullable
    public static Map<String, AttachmentAnimation.Clip> animations(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        if (AttachmentClient.hasNamedAnimation(ref)) return AttachmentClient.namedAnimation(ref);
        return ANIMATIONS.computeIfAbsent(ref, RigAssets::readAnimations).orElse(null);
    }

    /**
     * A clip by name: exact, else a Blockbench-prefixed name ending in {@code .<name>}
     * ({@code animation.ribbit.walk} for {@code walk}). Null when absent.
     */
    @Nullable
    public static AttachmentAnimation.Clip clip(@Nullable Map<String, AttachmentAnimation.Clip> clips, String name) {
        if (clips == null || clips.isEmpty() || name == null || name.isEmpty()) return null;
        AttachmentAnimation.Clip exact = clips.get(name);
        if (exact != null) return exact;
        for (Map.Entry<String, AttachmentAnimation.Clip> entry : clips.entrySet()) {
            if (entry.getKey().endsWith("." + name)) return entry.getValue();
        }
        return null;
    }

    /** A texture id: the synced pack texture when there is one, else the plain asset location. */
    @Nullable
    public static ResourceLocation texture(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        ResourceLocation synced = AttachmentClient.namedTexture(ref);
        return synced != null ? synced : DataPackLang.parseId(ref);
    }

    private static Optional<JsonObject> readGeometry(String ref) {
        byte[] bytes = read(ref);
        if (bytes == null) return Optional.empty();
        try {
            if (ref.endsWith(".bbmodel")) {
                String path = ref.substring(ref.lastIndexOf('/') + 1, ref.length() - ".bbmodel".length());
                bytes = BbmodelConverter.geometry(bytes, path, "");
                if (bytes == null) return Optional.empty();
            }
            return Optional.of(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            Townstead.LOGGER.warn("Rig geometry {} could not be parsed: {}", ref, e.getMessage());
            return Optional.empty();
        }
    }

    private static Optional<Map<String, AttachmentAnimation.Clip>> readAnimations(String ref) {
        byte[] bytes = read(ref);
        if (bytes == null) return Optional.empty();
        try {
            JsonObject json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> warnings = new ArrayList<>();
            Map<String, AttachmentAnimation.Clip> clips = AttachmentAnimation.parse(json, warnings);
            for (String warning : warnings) Townstead.LOGGER.debug("Rig animation {}: {}", ref, warning);
            return Optional.of(clips);
        } catch (Exception e) {
            Townstead.LOGGER.warn("Rig animation {} could not be parsed: {}", ref, e.getMessage());
            return Optional.empty();
        }
    }

    @Nullable
    private static byte[] read(String ref) {
        ResourceLocation id = DataPackLang.parseId(ref);
        if (id == null) return null;
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            Townstead.LOGGER.warn("Rig asset {} was not found in the data pack or client assets", ref);
            return null;
        }
        try (InputStream in = resource.get().open()) {
            return in.readAllBytes();
        } catch (Exception e) {
            Townstead.LOGGER.warn("Rig asset {} could not be read: {}", ref, e.getMessage());
            return null;
        }
    }
}
