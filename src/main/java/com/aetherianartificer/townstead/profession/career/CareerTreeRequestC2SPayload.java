package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player asks the named Scribe for the career tree (the "Careers" option in
 * the conversation screen). The server validates the Scribe, distance, and work hours
 * before answering; the client button is a convenience, never an authority.
 */
//? if neoforge {
public record CareerTreeRequestC2SPayload(int villagerId) implements CustomPacketPayload {
//?} else {
/*public record CareerTreeRequestC2SPayload(int villagerId) {
*///?}

    public void write(FriendlyByteBuf buf) { buf.writeVarInt(villagerId); }

    public static CareerTreeRequestC2SPayload read(FriendlyByteBuf buf) {
        return new CareerTreeRequestC2SPayload(buf.readVarInt());
    }

    //? if neoforge {
    public static final Type<CareerTreeRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_tree_request"));
    public static final StreamCodec<FriendlyByteBuf, CareerTreeRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerTreeRequestC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_tree_request");
    *///?}
}
