package com.aetherianartificer.townstead.reaction.net;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: tell the server "I just played the emote named X".
 * Sent by {@code EmotecraftEventBridge} when the local player plays a
 * known Emotecraft built-in via the B menu. The server broadcasts a
 * gesture event to nearby villagers so they can react.
 */
//? if neoforge {
public record GestureNotifyC2SPayload(String emoteName) implements CustomPacketPayload {
//?} else {
/*public record GestureNotifyC2SPayload(String emoteName) {
*///?}

    //? if neoforge {
    public static final Type<GestureNotifyC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "gesture_notify_c2s"));

    public static final StreamCodec<FriendlyByteBuf, GestureNotifyC2SPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, GestureNotifyC2SPayload::emoteName,
                    GestureNotifyC2SPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "gesture_notify_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "gesture_notify_c2s");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeUtf(emoteName);
    }

    public static GestureNotifyC2SPayload read(FriendlyByteBuf buf) {
        return new GestureNotifyC2SPayload(buf.readUtf());
    }
    *///?}
}
