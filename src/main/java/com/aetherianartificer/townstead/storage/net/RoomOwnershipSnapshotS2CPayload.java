package com.aetherianartificer.townstead.storage.net;

import com.aetherianartificer.townstead.Townstead;
import com.aetherianartificer.townstead.storage.RoomOwner;
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

/** Server-authoritative people available in the room ownership editor. */
//? if neoforge {
public record RoomOwnershipSnapshotS2CPayload(BlockPos tagPos, long worksiteId, String placeName,
                                               OwnershipScope scope, boolean privateAccess,
                                               boolean wholeBuildingAvailable,
                                               List<Person> people) implements CustomPacketPayload {
//?} else {
/*public record RoomOwnershipSnapshotS2CPayload(BlockPos tagPos, long worksiteId, String placeName,
                                               OwnershipScope scope, boolean privateAccess,
                                               boolean wholeBuildingAvailable,
                                               List<Person> people) {
*///?}
    public record Person(UUID uuid, String name, RoomOwner.Kind kind, boolean selected,
                         boolean homeInRoom, boolean homeInBuilding) {}

    public RoomOwnershipSnapshotS2CPayload {
        tagPos = tagPos == null ? BlockPos.ZERO : tagPos.immutable();
        placeName = placeName == null ? "" : placeName;
        scope = scope == null ? OwnershipScope.ROOM : scope;
        people = people == null ? List.of() : List.copyOf(people);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeBlockPos(tagPos);
        buf.writeLong(worksiteId);
        buf.writeUtf(placeName, 128);
        buf.writeEnum(scope);
        buf.writeBoolean(privateAccess);
        buf.writeBoolean(wholeBuildingAvailable);
        buf.writeVarInt(people.size());
        for (Person person : people) {
            buf.writeUUID(person.uuid());
            buf.writeUtf(person.name(), 128);
            buf.writeEnum(person.kind());
            buf.writeBoolean(person.selected());
            buf.writeBoolean(person.homeInRoom());
            buf.writeBoolean(person.homeInBuilding());
        }
    }

    public static RoomOwnershipSnapshotS2CPayload read(FriendlyByteBuf buf) {
        BlockPos tagPos = buf.readBlockPos();
        long worksiteId = buf.readLong();
        String placeName = buf.readUtf(128);
        OwnershipScope scope = buf.readEnum(OwnershipScope.class);
        boolean privateAccess = buf.readBoolean();
        boolean wholeBuildingAvailable = buf.readBoolean();
        int count = Math.min(1024, buf.readVarInt());
        List<Person> people = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            people.add(new Person(buf.readUUID(), buf.readUtf(128), buf.readEnum(RoomOwner.Kind.class),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));
        }
        return new RoomOwnershipSnapshotS2CPayload(tagPos, worksiteId, placeName, scope,
                privateAccess, wholeBuildingAvailable, people);
    }

    //? if neoforge {
    public static final Type<RoomOwnershipSnapshotS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Townstead.MOD_ID, "room_ownership_snapshot"));
    public static final StreamCodec<FriendlyByteBuf, RoomOwnershipSnapshotS2CPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), RoomOwnershipSnapshotS2CPayload::read);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    //?}
}
