package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player picked another career in the open Career screen and needs its tree. The server
 * answers with a fresh record of whoever that screen is showing, with the picked career in full.
 */
//? if neoforge {
public record CareerFocusC2SPayload(String careerId) implements CustomPacketPayload {
//?} else {
/*public record CareerFocusC2SPayload(String careerId) {
*///?}

    public void write(FriendlyByteBuf buf) { buf.writeUtf(careerId); }

    public static CareerFocusC2SPayload read(FriendlyByteBuf buf) {
        return new CareerFocusC2SPayload(buf.readUtf());
    }

    //? if neoforge {
    public static final Type<CareerFocusC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_focus"));
    public static final StreamCodec<FriendlyByteBuf, CareerFocusC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerFocusC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_focus");
    *///?}
}
