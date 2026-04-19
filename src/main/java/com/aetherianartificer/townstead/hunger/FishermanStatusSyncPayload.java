package com.aetherianartificer.townstead.hunger;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

//? if neoforge {
public record FishermanStatusSyncPayload(int entityId, String blockedReasonId) implements CustomPacketPayload {
//?} else {
/*public record FishermanStatusSyncPayload(int entityId, String blockedReasonId) {
*///?}

    //? if neoforge {
    public static final Type<FishermanStatusSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fisherman_status_sync"));

    public static final StreamCodec<FriendlyByteBuf, FishermanStatusSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, FishermanStatusSyncPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, FishermanStatusSyncPayload::blockedReasonId,
                    FishermanStatusSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "fisherman_status_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "fisherman_status_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(blockedReasonId);
    }

    public static FishermanStatusSyncPayload read(FriendlyByteBuf buf) {
        return new FishermanStatusSyncPayload(buf.readVarInt(), buf.readUtf());
    }
    *///?}
}
