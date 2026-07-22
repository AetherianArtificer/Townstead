package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player toggles goal tracking for a specialization from the Career screen. Tracked
 * goals notify when their requirements are finally within reach; the server owns the set.
 */
//? if neoforge {
public record CareerTrackC2SPayload(String careerId) implements CustomPacketPayload {
//?} else {
/*public record CareerTrackC2SPayload(String careerId) {
*///?}

    public void write(FriendlyByteBuf buf) { buf.writeUtf(careerId); }

    public static CareerTrackC2SPayload read(FriendlyByteBuf buf) {
        return new CareerTrackC2SPayload(buf.readUtf());
    }

    //? if neoforge {
    public static final Type<CareerTrackC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_track"));
    public static final StreamCodec<FriendlyByteBuf, CareerTrackC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerTrackC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_track");
    *///?}
}
