package com.aetherianartificer.townstead.rebirth;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: offer "Where you died" in the Destiny that follows a rebirth. Only needed where
 * MCA's Destiny reads its locations from config; elsewhere the list travels with MCA's own request.
 */
//? if neoforge {
public record RebirthDestinyS2CPayload() implements CustomPacketPayload {
//?} else {
/*public record RebirthDestinyS2CPayload() {
*///?}

    //? if neoforge {
    public static final Type<RebirthDestinyS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "rebirth_destiny_s2c"));

    public static final StreamCodec<FriendlyByteBuf, RebirthDestinyS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), RebirthDestinyS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    public void write(FriendlyByteBuf buf) {}

    public static RebirthDestinyS2CPayload read(FriendlyByteBuf buf) {
        return new RebirthDestinyS2CPayload();
    }
}
