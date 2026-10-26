package com.aetherianartificer.townstead.recognition;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/** Where an enclosure the player is working on is open, for the client to outline. */
//? if neoforge {
public record EnclosureGapS2CPayload(BlockPos pos) implements CustomPacketPayload {
//?} else {
/*public record EnclosureGapS2CPayload(BlockPos pos) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeBlockPos(pos); }

    public static EnclosureGapS2CPayload read(FriendlyByteBuf buf) { return new EnclosureGapS2CPayload(buf.readBlockPos()); }

    //? if neoforge {
    public static final Type<EnclosureGapS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "enclosure_gap"));
    public static final StreamCodec<FriendlyByteBuf, EnclosureGapS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), EnclosureGapS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "enclosure_gap");
    *///?}
}
