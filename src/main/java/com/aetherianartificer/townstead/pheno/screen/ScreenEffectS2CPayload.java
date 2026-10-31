package com.aetherianartificer.townstead.pheno.screen;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}

/** One screen cue for the receiving player: a flash, vignette, tint, shake or field-of-view kick. */
//? if neoforge {
public record ScreenEffectS2CPayload(int kind, int color, float intensity, int duration) implements CustomPacketPayload {
//?} else {
/*public record ScreenEffectS2CPayload(int kind, int color, float intensity, int duration) {
*///?}

    //? if neoforge {
    public static final Type<ScreenEffectS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "screen_effect"));

    public static final StreamCodec<FriendlyByteBuf, ScreenEffectS2CPayload> STREAM_CODEC =
            StreamCodec.of(ScreenEffectS2CPayload::write, ScreenEffectS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    public static void write(FriendlyByteBuf buf, ScreenEffectS2CPayload payload) {
        buf.writeVarInt(payload.kind);
        buf.writeInt(payload.color);
        buf.writeFloat(payload.intensity);
        buf.writeVarInt(payload.duration);
    }

    public static ScreenEffectS2CPayload read(FriendlyByteBuf buf) {
        return new ScreenEffectS2CPayload(buf.readVarInt(), buf.readInt(), buf.readFloat(), buf.readVarInt());
    }

    /** Client side: start the cue. */
    public static void accept(ScreenEffectS2CPayload payload) {
        ScreenEffect.Kind[] kinds = ScreenEffect.Kind.values();
        if (payload.kind < 0 || payload.kind >= kinds.length) return;
        ScreenEffect.Client.start(kinds[payload.kind], payload.color, payload.intensity, payload.duration);
    }
}
