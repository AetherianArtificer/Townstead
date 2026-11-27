package com.aetherianartificer.townstead.root.attachment;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: one piece of a worn attachment just came off. A client that is drawing the bearer
 * lets that piece fall. It changes nothing else: what's lost arrives with the body's values.
 */
//? if neoforge {
public record BodyPartLostS2CPayload(int entityId, String attachment, int piece) implements CustomPacketPayload {
//?} else {
/*public record BodyPartLostS2CPayload(int entityId, String attachment, int piece) {
*///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(attachment, 256);
        buf.writeVarInt(piece);
    }

    public static BodyPartLostS2CPayload read(FriendlyByteBuf buf) {
        return new BodyPartLostS2CPayload(buf.readVarInt(), buf.readUtf(256), buf.readVarInt());
    }

    //? if neoforge {
    public static final Type<BodyPartLostS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "body_part_lost"));
    public static final StreamCodec<FriendlyByteBuf, BodyPartLostS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), BodyPartLostS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
}
