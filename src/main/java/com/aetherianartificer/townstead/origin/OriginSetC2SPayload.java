package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Client → server: set a target's origin. {@code entityId == -1} targets the
 * sending player (their {@link PlayerOrigin}); otherwise the loaded villager
 * with that network id. An empty {@code originId} is a request only — the server
 * replies with the current value via {@link OriginSyncS2CPayload} and changes
 * nothing.
 */
//? if neoforge {
public record OriginSetC2SPayload(int entityId, String originId) implements CustomPacketPayload {
//?} else {
/*public record OriginSetC2SPayload(int entityId, String originId) {
*///?}

    public static final int SELF = -1;

    //? if neoforge {
    public static final Type<OriginSetC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_set_c2s"));

    public static final StreamCodec<FriendlyByteBuf, OriginSetC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OriginSetC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_set_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "origin_set_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(originId);
    }

    public static OriginSetC2SPayload read(FriendlyByteBuf buf) {
        return new OriginSetC2SPayload(buf.readInt(), buf.readUtf());
    }
}
