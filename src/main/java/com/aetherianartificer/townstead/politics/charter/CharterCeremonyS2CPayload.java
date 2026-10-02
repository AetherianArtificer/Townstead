package com.aetherianartificer.townstead.politics.charter;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
//?}

/** Client-local ceremony cue: what was proclaimed, and how far its wave runs, so each player's accessibility settings apply. */
//? if neoforge {
public record CharterCeremonyS2CPayload(BlockPos bell, CharterSnapshotS2CPayload.Text title, CharterSnapshotS2CPayload.Text subtitle,
                                        int radius) implements CustomPacketPayload {
    public static final Type<CharterCeremonyS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("townstead", "charter_ceremony"));
    public static final StreamCodec<FriendlyByteBuf, CharterCeremonyS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, value) -> value.write(buf), CharterCeremonyS2CPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
//?} else {
/*public record CharterCeremonyS2CPayload(BlockPos bell, CharterSnapshotS2CPayload.Text title, CharterSnapshotS2CPayload.Text subtitle,
                                        int radius) {
*///?}
    public void write(FriendlyByteBuf buf) { buf.writeBlockPos(bell); title.write(buf); subtitle.write(buf); buf.writeVarInt(radius); }
    public static CharterCeremonyS2CPayload read(FriendlyByteBuf buf) {
        return new CharterCeremonyS2CPayload(buf.readBlockPos(), CharterSnapshotS2CPayload.Text.read(buf),
                CharterSnapshotS2CPayload.Text.read(buf), Math.max(1, Math.min(128, buf.readVarInt())));
    }
}
