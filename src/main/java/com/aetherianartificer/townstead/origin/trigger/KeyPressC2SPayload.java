package com.aetherianartificer.townstead.origin.trigger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: the player pressed a bound key ({@code jump}, {@code sneak}, ...). The server
 * runs that player's {@code press} triggers for that key (condition checked server-side; the client
 * only reports the press, it never decides the effect).
 */
//? if neoforge {
public record KeyPressC2SPayload(String key) implements CustomPacketPayload {
//?} else {
/*public record KeyPressC2SPayload(String key) {
*///?}

    //? if neoforge {
    public static final Type<KeyPressC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "key_press_c2s"));

    public static final StreamCodec<FriendlyByteBuf, KeyPressC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), KeyPressC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "key_press_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "key_press_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(key);
    }

    public static KeyPressC2SPayload read(FriendlyByteBuf buf) {
        return new KeyPressC2SPayload(buf.readUtf());
    }
}
