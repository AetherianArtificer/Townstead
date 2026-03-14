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
public record ButcherStatusSyncPayload(int entityId, String blockedReasonId) implements CustomPacketPayload {
//?} else {
/*public record ButcherStatusSyncPayload(int entityId, String blockedReasonId) {
*///?}

    //? if neoforge {
    public static final Type<ButcherStatusSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "butcher_status_sync"));

    public static final StreamCodec<FriendlyByteBuf, ButcherStatusSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ButcherStatusSyncPayload::entityId,
                    ByteBufCodecs.STRING_UTF8, ButcherStatusSyncPayload::blockedReasonId,
                    ButcherStatusSyncPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "butcher_status_sync");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "butcher_status_sync");
    *///?}

    //? if forge {
    /*public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeUtf(blockedReasonId);
    }

    public static ButcherStatusSyncPayload read(FriendlyByteBuf buf) {
        return new ButcherStatusSyncPayload(buf.readVarInt(), buf.readUtf());
    }
    *///?}
}
