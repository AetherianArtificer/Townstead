package com.aetherianartificer.townstead.chronicle.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Server-authorized request to open a physical Chronicle source. */
//? if neoforge {
public record ChronicleOpenS2CPayload(String villageName) implements CustomPacketPayload {
//?} else {
/*public record ChronicleOpenS2CPayload(String villageName) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeUtf(villageName); }

    public static ChronicleOpenS2CPayload read(FriendlyByteBuf buf) {
        return new ChronicleOpenS2CPayload(buf.readUtf());
    }

    //? if neoforge {
    public static final Type<ChronicleOpenS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "chronicle_open"));
    public static final StreamCodec<FriendlyByteBuf, ChronicleOpenS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), ChronicleOpenS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "chronicle_open");
    *///?}
}
