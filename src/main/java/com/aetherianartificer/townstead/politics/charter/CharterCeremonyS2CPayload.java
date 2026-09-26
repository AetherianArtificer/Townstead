package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/** Client-local ceremony cue so visual effects can honor each player's accessibility settings. */
//? if neoforge {
public record CharterCeremonyS2CPayload(BlockPos bell, String settlement) implements CustomPacketPayload {
    public static final Type<CharterCeremonyS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead", "charter_ceremony"));
    public static final StreamCodec<FriendlyByteBuf, CharterCeremonyS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), CharterCeremonyS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record CharterCeremonyS2CPayload(BlockPos bell, String settlement) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeBlockPos(bell); buf.writeUtf(settlement, 128); }
    public static CharterCeremonyS2CPayload read(FriendlyByteBuf buf) {
        return new CharterCeremonyS2CPayload(buf.readBlockPos(), buf.readUtf(128));
    }
}
