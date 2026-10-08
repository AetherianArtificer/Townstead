package com.aetherianartificer.townstead.seal;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** The player chose a device or ink for their seal. The server checks both before keeping it. */
//? if neoforge {
public record SealC2SPayload(String device, int dye) implements CustomPacketPayload {
//?} else {
/*public record SealC2SPayload(String device, int dye) {
*///?}
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(device, 200);
        buf.writeVarInt(dye);
    }

    public static SealC2SPayload read(FriendlyByteBuf buf) {
        return new SealC2SPayload(buf.readUtf(200), buf.readVarInt());
    }

    //? if neoforge {
    public static final Type<SealC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "seal_choose"));
    public static final StreamCodec<FriendlyByteBuf, SealC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), SealC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "seal_choose");
    *///?}
}
