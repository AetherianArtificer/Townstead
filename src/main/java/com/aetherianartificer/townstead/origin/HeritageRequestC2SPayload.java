package com.aetherianartificer.townstead.origin;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server: ask for a {@link HeritageSyncPayload} for the villager with this
 * UUID, sent when the Heritage screen opens. Keyed on UUID (not network id) so it
 * resolves any villager the server has loaded — including a relative reached through
 * the family tree that isn't near the requesting client.
 */
//? if neoforge {
public record HeritageRequestC2SPayload(UUID villagerUuid) implements CustomPacketPayload {
//?} else {
/*public record HeritageRequestC2SPayload(UUID villagerUuid) {
*///?}

    //? if neoforge {
    public static final Type<HeritageRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "heritage_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, HeritageRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), HeritageRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "heritage_request_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "heritage_request_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeUUID(villagerUuid);
    }

    public static HeritageRequestC2SPayload read(FriendlyByteBuf buf) {
        return new HeritageRequestC2SPayload(buf.readUUID());
    }
}
