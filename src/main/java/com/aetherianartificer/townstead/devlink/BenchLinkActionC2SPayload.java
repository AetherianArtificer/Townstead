package com.aetherianartificer.townstead.devlink;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Client → server: a button on the Bench Link screen. The server re-checks permission for each. */
//? if neoforge {
public record BenchLinkActionC2SPayload(int action) implements CustomPacketPayload {
//?} else {
/*public record BenchLinkActionC2SPayload(int action) {
*///?}

    public static final int REFRESH = 0;
    public static final int START = 1;
    public static final int STOP = 2;
    public static final int CLEAR_SUBJECT = 3;

    //? if neoforge {
    public static final Type<BenchLinkActionC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "bench_link_action"));

    public static final StreamCodec<FriendlyByteBuf, BenchLinkActionC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), BenchLinkActionC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(action);
    }

    public static BenchLinkActionC2SPayload read(FriendlyByteBuf buf) {
        return new BenchLinkActionC2SPayload(buf.readVarInt());
    }
}
