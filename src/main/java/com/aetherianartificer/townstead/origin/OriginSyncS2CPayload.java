package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

/**
 * Server → client: the current origin id of a target. {@code entityId == -1}
 * is the player's own origin; otherwise a villager. Updates
 * {@code OriginClientStore}.
 */
//? if neoforge {
public record OriginSyncS2CPayload(int entityId, String originId) implements CustomPacketPayload {
//?} else {
/*public record OriginSyncS2CPayload(int entityId, String originId) {
*///?}

    //? if neoforge {
    public static final Type<OriginSyncS2CPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_sync_s2c"));

    public static final StreamCodec<FriendlyByteBuf, OriginSyncS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), OriginSyncS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "origin_sync_s2c");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "origin_sync_s2c");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeUtf(originId);
    }

    public static OriginSyncS2CPayload read(FriendlyByteBuf buf) {
        return new OriginSyncS2CPayload(buf.readInt(), buf.readUtf());
    }
}
