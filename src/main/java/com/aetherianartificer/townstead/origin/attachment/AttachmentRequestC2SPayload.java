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
 * Client → server: the SHA-1 hashes of attachment blobs the client doesn't already
 * have cached. The server replies with chunked {@link AttachmentChunkS2CPayload}s.
 */
//? if neoforge {
public record AttachmentRequestC2SPayload(List<String> hashes) implements CustomPacketPayload {
//?} else {
/*public record AttachmentRequestC2SPayload(java.util.List<String> hashes) {
*///?}

    //? if neoforge {
    public static final Type<AttachmentRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), AttachmentRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "attachment_request_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "attachment_request_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(hashes.size());
        for (String hash : hashes) buf.writeUtf(hash);
    }

    public static AttachmentRequestC2SPayload read(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> hashes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) hashes.add(buf.readUtf());
        return new AttachmentRequestC2SPayload(hashes);
    }
}
