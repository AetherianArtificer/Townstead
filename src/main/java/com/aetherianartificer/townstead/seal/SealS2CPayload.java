package com.aetherianartificer.townstead.seal;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** The player's own seal, sent on login and whenever they change it. */
//? if neoforge {
public record SealS2CPayload(PersonalSeal seal) implements CustomPacketPayload {
//?} else {
/*public record SealS2CPayload(PersonalSeal seal) {
*///?}
    public void write(FriendlyByteBuf buf) { seal.write(buf); }

    public static SealS2CPayload read(FriendlyByteBuf buf) { return new SealS2CPayload(PersonalSeal.read(buf)); }

    //? if neoforge {
    public static final Type<SealS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "seal_sync"));
    public static final StreamCodec<FriendlyByteBuf, SealS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), SealS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "seal_sync");
    *///?}
}
