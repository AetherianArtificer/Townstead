package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client -> Server page request against the chronicle. Pull-only: the client
 * never receives the template registry or unsolicited history.
 */
//? if neoforge {
public record ChronicleQueryC2SPayload(int requestId, byte mode, UUID subject,
                                       long beforeEventId, byte pageSize) implements CustomPacketPayload {
//?} else {
/*public record ChronicleQueryC2SPayload(int requestId, byte mode, UUID subject,
                                       long beforeEventId, byte pageSize) {
*///?}

    public static final byte MODE_VILLAGE = 0;
    public static final byte MODE_KNOWS = 1;
    public static final byte MODE_MEMORIES = 2;

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(requestId);
        buf.writeByte(mode);
        buf.writeUUID(subject);
        buf.writeLong(beforeEventId);
        buf.writeByte(pageSize);
    }

    public static ChronicleQueryC2SPayload read(FriendlyByteBuf buf) {
        return new ChronicleQueryC2SPayload(buf.readVarInt(), buf.readByte(), buf.readUUID(),
                buf.readLong(), buf.readByte());
    }

    //? if neoforge {
    public static final Type<ChronicleQueryC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "chronicle_query"));

    public static final StreamCodec<FriendlyByteBuf, ChronicleQueryC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ChronicleQueryC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "chronicle_query");
    *///?}
}
