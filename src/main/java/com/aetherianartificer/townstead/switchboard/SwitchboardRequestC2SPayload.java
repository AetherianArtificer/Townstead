package com.aetherianartificer.townstead.switchboard;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client to server: an operator asks to open the Switchboard. */
//? if neoforge {
public record SwitchboardRequestC2SPayload() implements CustomPacketPayload {
//?} else {
/*public record SwitchboardRequestC2SPayload() {
*///?}

    //? if neoforge {
    public static final Type<SwitchboardRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "switchboard_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, SwitchboardRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), SwitchboardRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {}

    public static SwitchboardRequestC2SPayload read(FriendlyByteBuf buf) {
        return new SwitchboardRequestC2SPayload();
    }
}
