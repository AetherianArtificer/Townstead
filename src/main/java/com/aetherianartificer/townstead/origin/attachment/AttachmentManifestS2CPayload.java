package com.aetherianartificer.townstead.origin.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → client: the attachment manifest (every {@link AttachmentDef} and slot,
 * each def carrying the SHA-1 of its geometry and texture). The client diffs this
 * against its on-disk cache and requests only the blobs it lacks.
 */
//? if neoforge {
public record AttachmentManifestS2CPayload(List<AttachmentDef> defs, List<AttachmentPointDef> slots,
                                           java.util.Map<String, String> namedTextures)
        implements CustomPacketPayload {
//?} else {
/*public record AttachmentManifestS2CPayload(java.util.List<AttachmentDef> defs, java.util.List<AttachmentPointDef> slots,
                                           java.util.Map<String, String> namedTextures) {
*///?}

    //? if neoforge {
    public static final Type<AttachmentManifestS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_manifest_s2c"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentManifestS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AttachmentManifestS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_manifest_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "attachment_manifest_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(defs.size());
        for (AttachmentDef def : defs) {
            buf.writeUtf(def.id());
            buf.writeUtf(def.geoSha1());
            buf.writeUtf(def.textureSha1());
            buf.writeUtf(def.targetTag() == null ? "" : def.targetTag());
            buf.writeUtf(def.targetPoint() == null ? "" : def.targetPoint());
            buf.writeUtf(def.bone());
            writeVec(buf, def.offset());
            writeVec(buf, def.rotation());
            buf.writeFloat(def.scale());
            buf.writeInt(def.tint());
        }
        buf.writeVarInt(slots.size());
        for (AttachmentPointDef point : slots) {
            buf.writeUtf(point.id());
            buf.writeUtf(point.bone());
            writeVec(buf, point.offset());
            buf.writeVarInt(point.tags().size());
            for (String tag : point.tags()) buf.writeUtf(tag);
        }
        buf.writeVarInt(namedTextures.size());
        for (java.util.Map.Entry<String, String> e : namedTextures.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeUtf(e.getValue());
        }
    }

    public static AttachmentManifestS2CPayload read(FriendlyByteBuf buf) {
        int defCount = buf.readVarInt();
        List<AttachmentDef> defs = new ArrayList<>(defCount);
        for (int i = 0; i < defCount; i++) {
            String id = buf.readUtf();
            String geo = buf.readUtf();
            String tex = buf.readUtf();
            String targetTag = buf.readUtf();
            String targetPoint = buf.readUtf();
            String bone = buf.readUtf();
            float[] offset = readVec(buf);
            float[] rotation = readVec(buf);
            float scale = buf.readFloat();
            int tint = buf.readInt();
            defs.add(new AttachmentDef(id, geo, tex, targetTag.isEmpty() ? null : targetTag,
                    targetPoint.isEmpty() ? null : targetPoint, bone, offset, rotation, scale, tint));
        }
        int slotCount = buf.readVarInt();
        List<AttachmentPointDef> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++) {
            String id = buf.readUtf();
            String bone = buf.readUtf();
            float[] offset = readVec(buf);
            int tagCount = buf.readVarInt();
            List<String> tags = new ArrayList<>(tagCount);
            for (int t = 0; t < tagCount; t++) tags.add(buf.readUtf());
            slots.add(new AttachmentPointDef(id, bone, offset, tags));
        }
        int texCount = buf.readVarInt();
        java.util.Map<String, String> namedTextures = new java.util.LinkedHashMap<>();
        for (int i = 0; i < texCount; i++) {
            String key = buf.readUtf();
            namedTextures.put(key, buf.readUtf());
        }
        return new AttachmentManifestS2CPayload(defs, slots, namedTextures);
    }

    private static void writeVec(FriendlyByteBuf buf, float[] v) {
        buf.writeFloat(v[0]);
        buf.writeFloat(v[1]);
        buf.writeFloat(v[2]);
    }

    private static float[] readVec(FriendlyByteBuf buf) {
        return new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()};
    }
}
