package com.aetherianartificer.townstead.rebirth;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client to server, from the death screen: the next respawn is a rebirth under this name. */
//? if neoforge {
public record RebirthRequestC2SPayload(String name) implements CustomPacketPayload {
//?} else {
/*public record RebirthRequestC2SPayload(String name) {
*///?}

    //? if neoforge {
    public static final Type<RebirthRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "rebirth_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, RebirthRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RebirthRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(name, Rebirth.MAX_NAME_LENGTH);
    }

    public static RebirthRequestC2SPayload read(FriendlyByteBuf buf) {
        return new RebirthRequestC2SPayload(buf.readUtf(Rebirth.MAX_NAME_LENGTH));
    }
}
