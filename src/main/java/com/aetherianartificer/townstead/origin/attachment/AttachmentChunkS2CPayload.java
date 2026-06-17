package com.aetherianartificer.townstead.origin.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: one chunk of an attachment blob's bytes, addressed by SHA-1.
 * Blobs are split into {@value #CHUNK_SIZE}-byte chunks to stay under the custom
 * payload size limit; the client reassembles by hash, verifies, caches, and
 * materializes (geometry → model, texture → dynamic texture).
 */
//? if neoforge {
public record AttachmentChunkS2CPayload(String sha1, int index, int total, int kind, byte[] data)
        implements CustomPacketPayload {
//?} else {
/*public record AttachmentChunkS2CPayload(String sha1, int index, int total, int kind, byte[] data) {
*///?}

    public static final int CHUNK_SIZE = 512 * 1024;

    //? if neoforge {
    public static final Type<AttachmentChunkS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_chunk_s2c"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentChunkS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AttachmentChunkS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_chunk_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "attachment_chunk_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(sha1);
        buf.writeVarInt(index);
        buf.writeVarInt(total);
        buf.writeVarInt(kind);
        buf.writeByteArray(data);
    }

    public static AttachmentChunkS2CPayload read(FriendlyByteBuf buf) {
        return new AttachmentChunkS2CPayload(buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readByteArray());
    }
}
