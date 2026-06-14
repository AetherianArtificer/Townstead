package com.aetherianartificer.townstead.client.attachment;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.client.attachment.geo.BedrockGeometryLoader;
import com.aetherianartificer.townstead.origin.attachment.AttachmentDef;
import com.aetherianartificer.townstead.origin.attachment.AttachmentRequestC2SPayload;
import com.aetherianartificer.townstead.origin.attachment.AttachmentServerData;
import com.aetherianartificer.townstead.origin.attachment.AttachmentPointDef;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client store for datapack-synced attachments. Takes the manifest, pulls any blobs
 * it doesn't already have cached (chunked), then materializes geometry into baked
 * {@link ModelPart}s and textures into registered {@link DynamicTexture}s. The render
 * layer reads definitions and the materialized blobs from here.
 */
public final class AttachmentClient {

    private static final Map<String, AttachmentDef> DEFS = new ConcurrentHashMap<>();
    private static final Map<String, AttachmentPointDef> SLOTS = new ConcurrentHashMap<>();
    private static final Map<String, ModelPart> GEO = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> TEXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Buffer> BUFFERS = new ConcurrentHashMap<>();

    private AttachmentClient() {}

    private static final class Buffer {
        final int total;
        final int kind;
        final byte[][] parts;
        int received;

        Buffer(int total, int kind) {
            this.total = total;
            this.kind = kind;
            this.parts = new byte[total][];
        }
    }

    public static void onManifest(List<AttachmentDef> defs, List<AttachmentPointDef> slots) {
        DEFS.clear();
        SLOTS.clear();
        for (AttachmentDef def : defs) DEFS.put(def.id(), def);
        for (AttachmentPointDef slot : slots) SLOTS.put(slot.id(), slot);

        Map<String, Integer> needed = new LinkedHashMap<>();
        for (AttachmentDef def : defs) {
            needed.putIfAbsent(def.geoSha1(), AttachmentServerData.KIND_GEO);
            needed.putIfAbsent(def.textureSha1(), AttachmentServerData.KIND_TEXTURE);
        }

        List<String> request = new ArrayList<>();
        needed.forEach((sha1, kind) -> {
            if (GEO.containsKey(sha1) || TEXTURES.containsKey(sha1)) return;
            byte[] cached = AttachmentCache.read(sha1);
            if (cached != null) materialize(sha1, kind, cached);
            else request.add(sha1);
        });
        if (!request.isEmpty()) sendRequest(request);
    }

    public static void onChunk(String sha1, int index, int total, int kind, byte[] data) {
        if (GEO.containsKey(sha1) || TEXTURES.containsKey(sha1)) return;
        Buffer buffer = BUFFERS.computeIfAbsent(sha1, k -> new Buffer(total, kind));
        if (index < 0 || index >= buffer.parts.length || buffer.parts[index] != null) return;
        buffer.parts[index] = data;
        buffer.received++;
        if (buffer.received < buffer.total) return;

        BUFFERS.remove(sha1);
        int size = 0;
        for (byte[] part : buffer.parts) size += part.length;
        byte[] bytes = new byte[size];
        int pos = 0;
        for (byte[] part : buffer.parts) {
            System.arraycopy(part, 0, bytes, pos, part.length);
            pos += part.length;
        }
        AttachmentCache.write(sha1, bytes);
        materialize(sha1, buffer.kind, bytes);
    }

    private static void materialize(String sha1, int kind, byte[] bytes) {
        try {
            if (kind == AttachmentServerData.KIND_GEO) {
                var json = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                ModelPart part = BedrockGeometryLoader.parse(json);
                if (part != null) GEO.put(sha1, part);
            } else {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
                ResourceLocation id = ResourceLocation.tryParse(Townstead.MOD_ID + ":attachment/" + sha1);
                Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
                TEXTURES.put(sha1, id);
            }
        } catch (Exception e) {
            Townstead.LOGGER.error("Failed to materialize attachment blob {}", sha1, e);
        }
    }

    public static AttachmentDef def(String id) {
        return DEFS.get(id);
    }

    public static AttachmentPointDef slot(String id) {
        return SLOTS.get(id);
    }

    /** Every synced point carrying {@code tag} (so one attachment can fill several, e.g. both ears). */
    public static List<AttachmentPointDef> pointsWithTag(String tag) {
        List<AttachmentPointDef> out = new ArrayList<>();
        for (AttachmentPointDef point : SLOTS.values()) {
            if (point.tags().contains(tag)) out.add(point);
        }
        return out;
    }

    public static ModelPart geometry(String sha1) {
        return GEO.get(sha1);
    }

    public static ResourceLocation texture(String sha1) {
        return TEXTURES.get(sha1);
    }

    public static void clear() {
        DEFS.clear();
        SLOTS.clear();
        BUFFERS.clear();
        // Baked geometry and registered textures are kept: they're content-addressed
        // and reused if the same blobs appear again next session.
    }

    private static void sendRequest(List<String> hashes) {
        AttachmentRequestC2SPayload payload = new AttachmentRequestC2SPayload(hashes);
        //? if neoforge {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);
        //?} else {
        /*com.aetherianartificer.townstead.TownsteadNetwork.sendToServer(payload);
        *///?}
    }
}
