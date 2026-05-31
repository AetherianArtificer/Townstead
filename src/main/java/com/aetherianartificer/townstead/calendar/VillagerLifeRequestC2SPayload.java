package com.aetherianartificer.townstead.calendar;

import com.aetherianartificer.townstead.Townstead;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client → server: ask for a fresh {@link VillagerLifeSyncPayload} for the
 * villager identified by {@code villagerUuid}. The editor's preview villager is a
 * throwaway client entity, so we key on the real UUID; {@code previewEntityId} is
 * the editor entity's network id, and the reply is re-keyed to it so the editor's
 * client-side lookups find the snapshot.
 */
//? if neoforge {
public record VillagerLifeRequestC2SPayload(int previewEntityId, UUID villagerUuid) implements CustomPacketPayload {
//?} else {
/*public record VillagerLifeRequestC2SPayload(int previewEntityId, UUID villagerUuid) {
*///?}

    //? if neoforge {
    public static final Type<VillagerLifeRequestC2SPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_request_c2s"));

    public static final StreamCodec<FriendlyByteBuf, VillagerLifeRequestC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, p) -> p.write(buf), VillagerLifeRequestC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    //?}

    //? if neoforge {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "villager_life_request_c2s");
    //?} else {
    /*public static final ResourceLocation ID = new ResourceLocation(Townstead.MOD_ID, "villager_life_request_c2s");
    *///?}

    public void write(FriendlyByteBuf buf) {
        buf.writeInt(previewEntityId);
        buf.writeUUID(villagerUuid);
    }

    public static VillagerLifeRequestC2SPayload read(FriendlyByteBuf buf) {
        return new VillagerLifeRequestC2SPayload(buf.readInt(), buf.readUUID());
    }
}
