package com.aetherianartificer.townstead.profession.career;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * The player declares a new primary vocation ("Take up this work" on the career screen).
 * The server validates place (inside an Archives, or with an on-duty Scribe), eligibility,
 * and the daily limit; the client sends an intention, never an outcome.
 */
//? if neoforge {
public record CareerVocationC2SPayload(String careerId) implements CustomPacketPayload {
//?} else {
/*public record CareerVocationC2SPayload(String careerId) {
*///?}

    public void write(FriendlyByteBuf buf) { buf.writeUtf(careerId); }

    public static CareerVocationC2SPayload read(FriendlyByteBuf buf) {
        return new CareerVocationC2SPayload(buf.readUtf());
    }

    //? if neoforge {
    public static final Type<CareerVocationC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "career_vocation"));
    public static final StreamCodec<FriendlyByteBuf, CareerVocationC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), CareerVocationC2SPayload::read);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}
    //? if forge {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "career_vocation");
    *///?}
}
