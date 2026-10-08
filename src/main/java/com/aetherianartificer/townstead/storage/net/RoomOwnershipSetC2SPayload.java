package com.aetherianartificer.townstead.storage.net;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.storage.OwnershipScope;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
//? if neoforge {
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
//?}
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Saves the checked owner UUIDs for the room tag the player is still standing near. */
//? if neoforge {
public record RoomOwnershipSetC2SPayload(BlockPos tagPos, long worksiteId, OwnershipScope scope,
                                         boolean privateAccess,
                                         List<UUID> selectedOwners) implements CustomPacketPayload {
//?} else {
/*public record RoomOwnershipSetC2SPayload(BlockPos tagPos, long worksiteId, OwnershipScope scope,
                                         boolean privateAccess, List<UUID> selectedOwners) {
*///?}
    public RoomOwnershipSetC2SPayload {
        tagPos = tagPos == null ? BlockPos.ZERO : tagPos.immutable();
        scope = scope == null ? OwnershipScope.ROOM : scope;
        selectedOwners = selectedOwners == null ? List.of() : List.copyOf(selectedOwners);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(tagPos);
        buf.writeLong(worksiteId);
        buf.writeEnum(scope);
        buf.writeBoolean(privateAccess);
        buf.writeVarInt(selectedOwners.size());
        for (UUID uuid : selectedOwners) buf.writeUUID(uuid);
    }

    public static RoomOwnershipSetC2SPayload read(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        long worksiteId = buf.readLong();
        OwnershipScope scope = buf.readEnum(OwnershipScope.class);
        boolean privateAccess = buf.readBoolean();
        int count = Math.min(1024, buf.readVarInt());
        List<UUID> selected = new ArrayList<>(count);
        for (int i = 0; i < count; i++) selected.add(buf.readUUID());
        return new RoomOwnershipSetC2SPayload(pos, worksiteId, scope, privateAccess, selected);
    }

    //? if neoforge {
    public static final Type<RoomOwnershipSetC2SPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "room_ownership_set"));
    public static final StreamCodec<FriendlyByteBuf, RoomOwnershipSetC2SPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), RoomOwnershipSetC2SPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}
}
